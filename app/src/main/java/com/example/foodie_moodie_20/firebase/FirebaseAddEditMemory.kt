package com.example.foodie_moodie_20.firebase

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.example.foodie_moodie_20.R
import com.example.foodie_moodie_20.databinding.ActivityFirebaseAddEditMemoryBinding
import com.example.foodie_moodie_20.firebase.firebaseDataModels.Firebaseuser
import com.example.foodie_moodie_20.firebase.firebaseDataModels.Memory
import com.example.foodie_moodie_20.utils.Constants
import com.example.foodie_moodie_20.utils.GlideLoader
import com.example.foodie_moodie_20.utils.ImageFileExtension
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap

class FirebaseAddEditMemory : AppCompatActivity(), View.OnClickListener{
    private lateinit var mBinding : ActivityFirebaseAddEditMemoryBinding
    private val mFireStore = FirebaseFirestore.getInstance()
    private lateinit var mProgressDialog : Dialog
    private var mImageURI : Uri? = null
    private var mImageURL : String = ""
    private var mPrivacy : String = " "
    private var mName : String = " "

    private lateinit var cal : Calendar
    private lateinit var dateSetListener : DatePickerDialog.OnDateSetListener

    private var mLatitude : Double = 0.0
    private var mLongitude : Double = 0.0

    private var mMemoryModel : Memory? = null
    fun getCurrentUserID(): String {
        val currentUser = FirebaseAuth.getInstance().currentUser
        return currentUser!!.uid
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding= ActivityFirebaseAddEditMemoryBinding.inflate(layoutInflater)
        setContentView(mBinding.root)

        val fireStore = FirebaseFirestore.getInstance()
        fireStore.collection("users")
            .document(getCurrentUserID())
            .get()
            .addOnCompleteListener { snapshot->
                val user = snapshot.result.toObject(Firebaseuser::class.java)
                mName = user?.firstName!!
            }

        if(!Places.isInitialized()){
            Places.initialize(this,(Constants.API_KEYmaps))
        }

        if(intent.hasExtra("pass_memory")){
            mMemoryModel=intent.getParcelableExtra("pass_memory")

            mBinding.etTitle.setText(mMemoryModel!!.title)
            mBinding.etDescription.setText(mMemoryModel!!.description)
            mBinding.etCityName.setText(mMemoryModel!!.cityName)
            mBinding.etDate.setText(mMemoryModel!!.date)
            mBinding.etRating.setText(mMemoryModel!!.rating.toString())
            mBinding.etLocation.setText(mMemoryModel!!.location)
            GlideLoader(this).loadPicture(mMemoryModel!!.imageURI,mBinding.ivImageView)

            mLatitude=mMemoryModel!!.latitude
            mLongitude=mMemoryModel!!.longitude
            mImageURL=mMemoryModel!!.imageURL
            mImageURI= Uri.parse(mMemoryModel!!.imageURI)

            if(mMemoryModel!!.privacy=="public"){
                mBinding.rbPublic.isChecked=true
                mBinding.rbPrivate.isChecked=false
            }else{
                mBinding.rbPrivate.isChecked=true
                mBinding.rbPublic.isChecked=false
            }
            mPrivacy=mMemoryModel!!.privacy

        }

        cal= Calendar.getInstance()
        dateSetListener= DatePickerDialog.OnDateSetListener{ _, year, month, dayOfMonth->
            cal.set(Calendar.YEAR,year)
            cal.set(Calendar.MONTH,month)
            cal.set(Calendar.DAY_OF_MONTH,dayOfMonth)
            updateDateInVew()
        }

        mBinding.etDate.setOnClickListener(this)
        mBinding.tvAddImage.setOnClickListener(this)
        mBinding.tvAddImage.setOnClickListener(this)
        mBinding.etLocation.setOnClickListener(this)
        mBinding.btnSave.setOnClickListener(this)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onClick(v: View?) {
        when(v!!.id){
            R.id.etDate->{
                DatePickerDialog(this,
                    dateSetListener,
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                ).show()
            }
            R.id.tvAddImage->{
                val imageMethodDialog= AlertDialog.Builder(this)
                imageMethodDialog.setTitle("Select Action")

                val imageDialogItems=arrayOf("Select Photo From Gallery","Capture Photo From Camera")
                imageMethodDialog.setItems(imageDialogItems){_,which->
                    when(which){
                        0-> selectPhotoFromGallery()
                        1-> takePhotoFromCamera()
                    }
                }
                imageMethodDialog.show()
            }
            R.id.etLocation->{
                val fields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.ADDRESS)
                val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.FULLSCREEN,fields).build(this)
                @Suppress("DEPRECATION")
                startActivityForResult(intent,Constants.PLACE_AUTOCOMPLETE_REQUEST_CODE)
            }
            R.id.btnSave->{
                if(validateDetails()){
                    uploadDataToFirestore()
                }
            }
        }
    }

    private fun uploadDataToFirestore(){
        uploadImage()
    }

    private fun uploadImage(){
        showProgressDialog()
        //FireStoreClass().uploadImageToCloudStorage(this,mImageURI,Constants.IMAGE_NAME_IN_CLOUD)
        val sRef : StorageReference = FirebaseStorage.getInstance().reference.child(
            Constants.IMAGE_NAME_IN_CLOUD + System.currentTimeMillis() + "." + ImageFileExtension(this,mImageURI)
        )

        sRef.putFile(mImageURI!!)
            .addOnSuccessListener { taskSnapshot ->
                Log.e("Firebase Image URl",taskSnapshot.metadata!!.reference!!.downloadUrl.toString())
                taskSnapshot.metadata!!.reference!!.downloadUrl
                    .addOnSuccessListener { uri ->
                        Log.e("Downloadable Image Uri", uri.toString())
                        imageUploadSuccess(uri.toString())
                    }
            }
            .addOnFailureListener{exception ->
                hideProgressBar()
                Toast.makeText(this, "Error in Upload Image", Toast.LENGTH_SHORT).show()
            }
    }

    fun imageUploadSuccess(imageURl: String) {
        mImageURL=imageURl
        if(mMemoryModel!=null){
            updateMemory()
        }else{
            uploadMemory()
        }

    }

    private fun uploadMemory(){
        mPrivacy = if(mBinding.rbPublic.isChecked){
            Constants.PUBLIC
        }else{
            Constants.PRIVATE
        }

        val memory = Memory(
            mName,
            getCurrentUserID(),
            mBinding.etTitle.text.toString().trim { it<=' ' },
            mBinding.etDescription.text.toString().trim { it<=' ' },
            mBinding.etCityName.text.toString().trim { it<=' ' }.lowercase(Locale.getDefault()),
            mBinding.etDate.text.toString().trim { it<=' ' },
            mBinding.etLocation.text.toString().trim { it<=' ' },
            mLatitude,
            mLongitude,
            mImageURI.toString(),
            mImageURL,
            mPrivacy,
            Integer.parseInt(mBinding.etRating.text.toString().trim { it<=' ' })
        )
        //FireStoreClass().uploadMemory(this,memory)
        mFireStore.collection(Constants.MEMORIES)
            .document()
            .set(memory, SetOptions.merge())
            .addOnSuccessListener {
                memoryUploadSuccess()
            }
            .addOnFailureListener{
                hideProgressBar()
                Toast.makeText(this, "Error in Upload Memory", Toast.LENGTH_SHORT).show()
            }

    }

    private fun updateMemory(){
        showProgressDialog()
        val memoryHashMap=HashMap<String,Any>()

        memoryHashMap[Constants.TITLE]=mBinding.etTitle.text.toString().trim { it<=' ' }
        memoryHashMap[Constants.DESCRIPTION]=mBinding.etDescription.text.toString().trim { it<=' ' }
        memoryHashMap[Constants.CITY_NAME]=mBinding.etCityName.text.toString().trim { it<=' ' }
        memoryHashMap[Constants.DATE]=mBinding.etDate.text.toString().trim { it<=' ' }
        memoryHashMap[Constants.LOCATION]=mBinding.etLocation.text.toString().trim { it<=' ' }
        memoryHashMap[Constants.RATING]=Integer.parseInt(mBinding.etRating.text.toString().trim { it<=' ' })

        memoryHashMap[Constants.LATITUDE]=mLatitude
        memoryHashMap[Constants.LONGITUDE]=mLongitude
        memoryHashMap[Constants.IMAGE_URL]=mImageURL
        memoryHashMap[Constants.IMAGE_URI]=mImageURI.toString()

        memoryHashMap[Constants.PRIVACY]=if(mBinding.rbPublic.isChecked){
            Constants.PUBLIC
        }else{
            Constants.PRIVATE
        }

        //FireStoreClass().updateMemory(this,memoryHashMap,mMemoryModel!!.memoryID)

        mFireStore.collection(Constants.MEMORIES)
            .document(mMemoryModel!!.memoryID)
            .update(memoryHashMap)
            .addOnSuccessListener {
              updationSuccess()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error in UpdateMemory", Toast.LENGTH_SHORT).show()
               hideProgressBar()
                Log.e("Eroor","Error while updating the memory")
            }

    }

    fun updationSuccess(){
        hideProgressBar()
        Toast.makeText(this, "Memory updated successfully", Toast.LENGTH_SHORT).show()
        val intent= Intent(this,FirebaseUserActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    fun memoryUploadSuccess() {
        hideProgressBar()
        Toast.makeText(this, "Memory added successfully", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun takePhotoFromCamera() {
        Dexter.withContext(this)
            .withPermission(Manifest.permission.CAMERA).withListener(object : PermissionListener {
                override fun onPermissionGranted(response: PermissionGrantedResponse) {
                    val intent= Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent,Constants.CAMERA)
                }

                override fun onPermissionDenied(response: PermissionDeniedResponse) {
                    showDialogForPermission()
                }

                override fun onPermissionRationaleShouldBeShown(permission: PermissionRequest?, token: PermissionToken?) {
                    token?.continuePermissionRequest()
                }
            }).check()
    }

    private fun selectPhotoFromGallery() {
        Dexter.withContext(this)
            .withPermission(Manifest.permission.READ_EXTERNAL_STORAGE).withListener(object :
                PermissionListener {
                override fun onPermissionGranted(response: PermissionGrantedResponse) {
                    val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent,Constants.GALLERY)
                }

                override fun onPermissionDenied(response: PermissionDeniedResponse) {
                    showDialogForPermission()
                }

                override fun onPermissionRationaleShouldBeShown(permission: PermissionRequest?, token: PermissionToken?) {
                    token?.continuePermissionRequest()
                }
            }).check()
    }

    private fun showDialogForPermission(){
        AlertDialog.Builder(this)
            .setMessage("It looks like you have turned of permissions required for this feature. " +
                    "It can be enabled under application settings")
            .setPositiveButton("GO TO SETTINGS"){_,_ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package",packageName,null)
                intent.data=uri
                startActivity(intent)
            }
            .setNegativeButton("Cancel"){dialog,_->
                dialog.dismiss()
            }
            .show()
    }

    private fun updateDateInVew(){
        val myFormat="dd MMM yyyy"
        val sdf= SimpleDateFormat(myFormat, Locale.getDefault())
        mBinding.etDate.setText(sdf.format(cal.time).toString())
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if(resultCode== Activity.RESULT_OK){
            if(requestCode==Constants.GALLERY){
                if (data != null) {
                    mImageURI=data.data!!
                    GlideLoader(this).loadPicture(mImageURI!!,mBinding.ivImageView)
                }
            }else if(requestCode==Constants.CAMERA){
                val thumbnail : Bitmap =data!!.extras!!.get("data") as Bitmap
                GlideLoader(this).loadPicture(thumbnail,mBinding.ivImageView)

                val bytes = ByteArrayOutputStream()
                thumbnail.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
                @Suppress("DEPRECATION")
                val path: String = MediaStore.Images.Media.insertImage(this.contentResolver, thumbnail, "Title", null)
                mImageURI= Uri.parse(path)
            }else if(requestCode==Constants.PLACE_AUTOCOMPLETE_REQUEST_CODE){
                val places : Place = Autocomplete.getPlaceFromIntent(data!!)
                val fullAddress="${places.name}, ${places.address} "
                mBinding.etLocation.setText(fullAddress)
                mLatitude=places.latLng!!.latitude
                mLongitude=places.latLng!!.longitude
            }
        }

    }

    private fun validateDetails() : Boolean{
        return when{
            TextUtils.isEmpty(mBinding.etTitle.text.toString().trim { it<=' ' }) ->{
                showErrorSnackBar("Please Enter The Title")
                false
            }
            TextUtils.isEmpty(mBinding.etDescription.text.toString().trim { it<=' ' }) ->{
                showErrorSnackBar("Please Enter The Description")
                false
            }
            TextUtils.isEmpty(mBinding.etCityName.text.toString().trim { it<=' ' }) ->{
                showErrorSnackBar("Please Enter The City Name")
                false
            }
            TextUtils.isEmpty(mBinding.etDate.text.toString().trim { it<=' ' }) ->{
                showErrorSnackBar("Please Enter The Date")
                false
            }
            TextUtils.isEmpty(mBinding.etLocation.text.toString().trim { it<=' ' }) ->{
                showErrorSnackBar("Please Enter The Location")
                false
            }
            TextUtils.isEmpty(mBinding.etRating.text.toString().trim { it<=' ' }) ->{
                showErrorSnackBar("Please Enter The Rating")
                false
            }
            Integer.parseInt(mBinding.etRating.text.toString().trim { it<=' ' })>5 ->{
                showErrorSnackBar("Rating Should Be From 1 To 5")
                false
            }
            Integer.parseInt(mBinding.etRating.text.toString().trim { it<=' ' })<1 ->{
                showErrorSnackBar("Rating Should Be From 1 To 5")
                false
            }
            mImageURI==null->{
                showErrorSnackBar("Please Add The Image")
                false
            }
            else->{
                true
            }
        }
    }

    fun showProgressDialog(){
        mProgressDialog = Dialog(this,R.style.myDialogStyle)
        mProgressDialog.window!!.setBackgroundDrawableResource(android.R.color.transparent)
        mProgressDialog.setContentView(R.layout.dialog_progress)
        mProgressDialog.setCancelable(false)
        mProgressDialog.setCanceledOnTouchOutside(false)
        mProgressDialog.show()
    }

    fun hideProgressBar(){
        mProgressDialog.dismiss()
    }

    fun showErrorSnackBar(message:String){
        val snackBar : Snackbar = Snackbar.make(findViewById(android.R.id.content),message,
            Snackbar.LENGTH_SHORT)
        snackBar.setTextColor(ContextCompat.getColor(this,R.color.white))

        val snackBarView = snackBar.view
        snackBarView.setBackgroundColor(ContextCompat.getColor(this, R.color.bg_color_one))
        snackBar.show()
    }

}