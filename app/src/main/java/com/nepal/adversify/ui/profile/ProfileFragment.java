package com.nepal.adversify.ui.profile;


import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ProgressBar;

import com.bumptech.glide.Glide;
import com.generic.appbase.domain.dto.ClientInfo;
import com.generic.appbase.domain.dto.Location;
import com.generic.appbase.ui.BaseFragment;
import com.generic.appbase.utils.CommonUtils;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;
import com.mikhaellopez.circularimageview.CircularImageView;
import com.nepal.adversify.R;
import com.nepal.adversify.viewmodel.ClientViewModel;
import com.nepal.adversify.viewmodel.ClientViewModelFactory;

import java.util.List;
import java.util.Objects;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProviders;
import androidx.navigation.Navigation;
import androidx.navigation.ui.NavigationUI;
import io.reactivex.CompletableObserver;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import timber.log.Timber;

import static android.app.Activity.RESULT_OK;


public class ProfileFragment extends BaseFragment {

    private static final int OPEN_DOCUMENT_CODE = 2;

    @Inject
    ClientViewModelFactory mClientViewModelFactory;
    @Inject
    FusedLocationProviderClient mLocationProvider;
    @Inject
    CompositeDisposable mCompositeDisposable;

    private CircularImageView mAvatarImageView;
    private AppCompatImageView mImagePickerImageView;
    private TextInputEditText mNameInputEditText;
    private MaterialButton mUpdateButton;
    private ProgressBar mProgressBar;

    private ClientViewModel mClientViewModel;
    private Uri mSelectedImage;

    public ProfileFragment() {
        // Required empty public constructor
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mClientViewModel = ViewModelProviders.of(getActivity(), mClientViewModelFactory).get(ClientViewModel.class);
        observeData();
    }


    @SuppressLint("MissingPermission")
    @Override
    protected View onViewReady(View view, Bundle savedInstanceState) {

        Toolbar mToolbar = view.findViewById(R.id.toolbar);
        ((AppCompatActivity) Objects.requireNonNull(getActivity())).setSupportActionBar(mToolbar);
        NavigationUI.setupWithNavController(mToolbar, Navigation.findNavController(view));
        mToolbar.setTitle(R.string.title_profile);

        mAvatarImageView = view.findViewById(R.id.avatar);
        mImagePickerImageView = view.findViewById(R.id.avatar_icon);
        mNameInputEditText = view.findViewById(R.id.input_name);
        mUpdateButton = view.findViewById(R.id.update_button);
        mProgressBar = view.findViewById(R.id.progress);

        mImagePickerImageView.setOnClickListener((v) -> {

            Dexter.withActivity(getActivity())
                    .withPermissions(
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE

                    )
                    .withListener(new MultiplePermissionsListener() {
                        @Override
                        public void onPermissionsChecked(MultiplePermissionsReport report) {
                            if (report.areAllPermissionsGranted()) {
                                Timber.d("onPermissionGranted");
                                Intent galleryIntent = new Intent(Intent.ACTION_PICK,
                                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                                // Start the Intent
                                startActivityForResult(galleryIntent, OPEN_DOCUMENT_CODE);
                            }

                            if (report.isAnyPermissionPermanentlyDenied()) {
                                showToast("Permission denied permanently. Go to settings and enable them.");
                            }
                        }

                        @Override
                        public void onPermissionRationaleShouldBeShown(List<PermissionRequest> permissions, PermissionToken token) {
                            token.continuePermissionRequest();
                        }
                    })
                    .check();

        });
        mUpdateButton.setOnClickListener((v) -> {
            String name = mNameInputEditText.getEditableText().toString().trim();
            if (TextUtils.isEmpty(name)) {
                mNameInputEditText.setError("Please enter display name.");
                mNameInputEditText.requestFocus();
                return;
            }

            mNameInputEditText.onEditorAction(EditorInfo.IME_ACTION_DONE);

            mProgressBar.setVisibility(View.VISIBLE);
            mUpdateButton.setEnabled(false);
            mLocationProvider.getLastLocation()
                    .addOnSuccessListener(location -> {
                        ClientInfo clientInfo = new ClientInfo();
                        clientInfo.id = CommonUtils.getDeviceId(getContext());
                        clientInfo.name = name;
                        clientInfo.avatar = mSelectedImage == null ? "" : mSelectedImage.toString();
                        clientInfo.location = new Location();
                        clientInfo.location.lat = location.getLatitude();
                        clientInfo.location.lon = location.getLongitude();
                        mClientViewModel.saveClientInfo(clientInfo)
                                .subscribe(new CompletableObserver() {
                                    @Override
                                    public void onSubscribe(Disposable d) {
                                        mCompositeDisposable.add(d);
                                    }

                                    @Override
                                    public void onComplete() {
                                        mUpdateButton.setEnabled(true);
                                        mProgressBar.setVisibility(View.GONE);
                                        showToast("Client profile updated successfully.");
                                        Navigation.findNavController(view).navigateUp();
                                    }

                                    @Override
                                    public void onError(Throwable e) {
                                        Timber.e(e);
                                        mUpdateButton.setEnabled(true);
                                        mProgressBar.setVisibility(View.GONE);
                                        showToast("Error while saving client profile.");
                                    }
                                });


                    })
                    .addOnFailureListener(e -> {
                        Timber.e(e);
                        mUpdateButton.setEnabled(true);
                    });


        });


        return view;
    }

    private void observeData() {
        mClientViewModel.getClientLiveData().observe(this, data -> {
            Timber.d("Client observed data");
            if (data != null) {
                fillData(data);
            }
        });
    }

    private void fillData(ClientInfo data) {
        mNameInputEditText.setText(data.name);
        if (!TextUtils.isEmpty(data.avatar)) {
            Glide.with(getContext())
                    .load(data.avatar)
                    .into(mAvatarImageView);
            mSelectedImage = Uri.parse(data.avatar);
        }


    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == OPEN_DOCUMENT_CODE && resultCode == RESULT_OK) {
            if (resultData != null) {
                mSelectedImage = resultData.getData();
                Glide.with(getContext())
                        .load(mSelectedImage)
                        .into(mAvatarImageView);
            }
        }
    }


    @Override
    protected int getContentView() {
        return R.layout.fragment_profile;
    }

    @Override
    public void onDestroy() {
        mCompositeDisposable.dispose();
        super.onDestroy();
    }
}
