package com.vortexwolf.chan.activities;

import java.io.File;

import android.app.Activity;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.vortexwolf.chan.R;
import com.vortexwolf.chan.asynctasks.DownloadFileTask;
import com.vortexwolf.chan.common.Constants;
import com.vortexwolf.chan.common.Factory;
import com.vortexwolf.chan.common.Websites;
import com.vortexwolf.chan.common.utils.AppearanceUtils;
import com.vortexwolf.chan.common.utils.UriUtils;
import com.vortexwolf.chan.interfaces.IDownloadFileView;
import com.vortexwolf.chan.interfaces.IWebsite;
import com.vortexwolf.chan.models.presentation.GalleryItemViewBag;
import com.vortexwolf.chan.services.BrowserLauncher;
import com.vortexwolf.chan.services.CacheDirectoryManager;
import com.vortexwolf.chan.services.MyTracker;
import com.vortexwolf.chan.settings.ApplicationSettings;

public class BrowserActivity extends Activity {
    public static final String TAG = "BrowserActivity";

    private enum ViewType {
        PAGE, LOADING, ERROR
    }

    private final MyTracker mTracker = Factory.resolve(MyTracker.class);
    private final CacheDirectoryManager mCacheDirectoryManager = Factory.resolve(CacheDirectoryManager.class);
    private final ApplicationSettings mSettings = Factory.resolve(ApplicationSettings.class);

    private View mContainerView;
    private View mMainView = null;
    private View mLoadingView = null;
    private View mErrorView = null;
    private ProgressBar mProgressBar;

    private Uri mUri = null;
    private String mTitle = null;

    private Menu mMenu;
    private boolean mImageLoaded = false;
    private File mLoadedFile = null;
    private ViewType mViewType = null;

    private DownloadFileTask mCurrentTask = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.requestWindowFeature(Window.FEATURE_PROGRESS);

        this.setTheme(mSettings.getTheme());
        this.setContentView(R.layout.browser);

        if (this.mSettings.isLegacyImageViewer() || this.mSettings.isExternalVideoPlayer()) {
            this.mContainerView = this.findViewById(R.id.webview_container);
            this.mMainView = this.mContainerView.findViewById(R.id.webview);
            int background = AppearanceUtils.getThemeColor(this.getTheme(), R.styleable.Theme_activityRootBackground);
            AppearanceUtils.prepareWebView((WebView)this.mMainView, background);
        } else {
            this.mContainerView = this.findViewById(R.id.image_gallery_item_container);
            this.mContainerView.setVisibility(View.VISIBLE);
            this.findViewById(R.id.webview_container).setVisibility(View.GONE);
            this.mMainView = this.mContainerView.findViewById(R.id.image_layout);
        }

        this.mProgressBar = (ProgressBar) this.findViewById(R.id.page_progress_bar);
        this.mLoadingView = this.mContainerView.findViewById(R.id.loading);
        this.mErrorView = this.mContainerView.findViewById(R.id.error);

        this.mUri = this.getIntent().getData();
        this.mTitle = this.mUri.toString();
        this.setTitle(this.mTitle);

        this.loadImage();

        IWebsite website = Websites.fromUri(this.mUri);
        if (website != null) {
            this.mTracker.setBoardVar(website.getUrlParser().getBoardName(this.mUri));
        } else {
            this.mTracker.setBoardVar("");
        }
        this.mTracker.trackActivityView(TAG);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (this.mCurrentTask != null) {
            this.mCurrentTask.cancel(true);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = this.getMenuInflater();
        inflater.inflate(R.menu.browser, menu);

        this.mMenu = menu;
        this.updateOptionsMenu();

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.open_browser_menu_id:
                BrowserLauncher.launchExternalBrowser(this, this.mUri.toString());
                break;
            case R.id.play_video_menu_id:
                File cachedFile = this.mCacheDirectoryManager.getCachedImageFileForRead(this.mUri);
                if (cachedFile.exists()) {
                    BrowserLauncher.playVideoExternal(cachedFile, this);
                }
                break;
            case R.id.save_menu_id:
                new DownloadFileTask(this, this.mUri).execute();
                break;
            case R.id.refresh_menu_id:
                this.loadImage();
                break;
            case R.id.share_menu_id:
                Intent shareImageIntent = new Intent(Intent.ACTION_SEND);
                if (UriUtils.isImageUri(this.mUri)) {
                    shareImageIntent.setType("image/jpeg");
                } else if (UriUtils.isWebmUri(this.mUri)) {
                    shareImageIntent.setType("video/webm");
                }
                shareImageIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(this.mLoadedFile));
                this.startActivity(Intent.createChooser(shareImageIntent, this.getString(R.string.share_via)));
                break;
            case R.id.share_link_menu_id:
                Intent shareLinkIntent = new Intent(Intent.ACTION_SEND);
                shareLinkIntent.setType("text/plain");
                shareLinkIntent.putExtra(Intent.EXTRA_SUBJECT, this.mUri.toString());
                shareLinkIntent.putExtra(Intent.EXTRA_TEXT, this.mUri.toString());
                this.startActivity(Intent.createChooser(shareLinkIntent, this.getString(R.string.share_via)));
                break;
            case R.id.menu_search_tineye_id:
                String tineyeSearchUrl = "http://www.tineye.com/search?url=" + this.mUri;
                BrowserLauncher.launchExternalBrowser(this.getApplicationContext(), tineyeSearchUrl);
                break;
            case R.id.menu_search_google_id:
                String googleSearchUrl = "http://www.google.com/searchbyimage?image_url=" + this.mUri;
                BrowserLauncher.launchExternalBrowser(this.getApplicationContext(), googleSearchUrl);
                break;
            case R.id.menu_image_operations_id:
                String imageOpsUrl = "http://imgops.com/" + this.mUri;
                BrowserLauncher.launchExternalBrowser(this.getApplicationContext(), imageOpsUrl);
                break;
        }

        return true;
    }

    private void loadImage() {
        if (this.mCurrentTask != null) {
            this.mCurrentTask.cancel(true);
        }

        File cachedFile = this.mCacheDirectoryManager.getCachedImageFileForRead(this.mUri);
        if (cachedFile.exists()) {
            // show from cache
            this.setImage(cachedFile);
        } else {
            // download image and cache it
            File writeCachedFile = this.mCacheDirectoryManager.getCachedImageFileForWrite(this.mUri);
            this.mCurrentTask = new DownloadFileTask(this, this.mUri, writeCachedFile, new BrowserDownloadFileView(), false);
            this.mCurrentTask.execute();
        }
    }

    private void setImage(File file) {
        this.mLoadedFile = file;

        if (this.mSettings.isLegacyImageViewer() || this.mSettings.isExternalVideoPlayer()) {
            AppearanceUtils.setScaleWebView((WebView)this.mMainView, (View)this.mMainView.getParent(), file, this);
            ((WebView)this.mMainView).loadUrl(Uri.fromFile(file).toString());
        }
        else if (UriUtils.isWebmUri(this.mUri)) {
            GalleryItemViewBag vb = new GalleryItemViewBag();
            vb.layout = (FrameLayout)this.mMainView;
            vb.loading = this.mLoadingView;
            vb.error = this.mErrorView;

            AppearanceUtils.setVideoFile(this.mLoadedFile, this, vb);
        } else {
            int background = AppearanceUtils.getThemeColor(this.getTheme(), R.styleable.Theme_activityRootBackground);
            AppearanceUtils.setImage(file, this, (FrameLayout)mMainView, background);
        }

        this.mImageLoaded = true;
        this.updateOptionsMenu();
    }

    private void updateOptionsMenu() {
        if (this.mMenu == null) {
            return;
        }

        MenuItem saveMenuItem = this.mMenu.findItem(R.id.save_menu_id);
        MenuItem shareMenuItem = this.mMenu.findItem(R.id.share_menu_id);
        MenuItem refreshMenuItem = this.mMenu.findItem(R.id.refresh_menu_id);
        MenuItem playVideoMenuItem = this.mMenu.findItem(R.id.play_video_menu_id);
        MenuItem searchTineyeMenuItem = this.mMenu.findItem(R.id.menu_search_tineye_id);
        MenuItem searchGoogleMenuItem = this.mMenu.findItem(R.id.menu_search_google_id);
        MenuItem imageOpsMenuItem = this.mMenu.findItem(R.id.menu_image_operations_id);

        saveMenuItem.setVisible(this.mImageLoaded);
        shareMenuItem.setVisible(this.mImageLoaded);
        refreshMenuItem.setVisible(this.mViewType == ViewType.ERROR);
        playVideoMenuItem.setVisible(this.mImageLoaded && UriUtils.isWebmUri(this.mUri));
        searchTineyeMenuItem.setVisible(UriUtils.isImageUri(this.mUri));
        searchGoogleMenuItem.setVisible(UriUtils.isImageUri(this.mUri));
        imageOpsMenuItem.setVisible(UriUtils.isImageUri(this.mUri));
    }

    private void switchToLoadingView() {
        this.switchToView(ViewType.LOADING);
    }

    private void switchToPageView() {
        this.switchToView(ViewType.PAGE);
    }

    private void switchToErrorView(String message) {
        this.switchToView(ViewType.ERROR);
        this.updateOptionsMenu();

        TextView errorTextView = (TextView) this.mErrorView.findViewById(R.id.error_text);
        errorTextView.setText(message != null ? message : this.getString(R.string.error_unknown));
    }

    private void switchToView(ViewType vt) {
        this.mViewType = vt;
        if (vt == null) {
            return;
        }

        switch (vt) {
            case PAGE:
                this.mMainView.setVisibility(View.VISIBLE);
                this.mLoadingView.setVisibility(View.GONE);
                this.mErrorView.setVisibility(View.GONE);
                break;
            case LOADING:
                this.mMainView.setVisibility(View.GONE);
                this.mLoadingView.setVisibility(View.VISIBLE);
                this.mErrorView.setVisibility(View.GONE);
                break;
            case ERROR:
                this.mMainView.setVisibility(View.GONE);
                this.mLoadingView.setVisibility(View.GONE);
                this.mErrorView.setVisibility(View.VISIBLE);
                break;
        }
    }

    private void setProgressComp(int progress) {
        if (Constants.SDK_VERSION < 21) {
            this.setProgress(progress);
        } else if (progress == Window.PROGRESS_INDETERMINATE_ON) {
            this.mProgressBar.setVisibility(View.VISIBLE);
            this.mProgressBar.setIndeterminate(true);
        } else if (progress > 0 && progress < Window.PROGRESS_END) {
            this.mProgressBar.setVisibility(View.VISIBLE);
            this.mProgressBar.setIndeterminate(false);
            this.mProgressBar.setProgress(progress / 100);
        } else {
            this.mProgressBar.setVisibility(View.GONE);
        }
    }

    private class BrowserDownloadFileView implements IDownloadFileView {

        private double mMaxValue = -1;

        @Override
        public void setCurrentProgress(int value) {
            if (this.mMaxValue > 0) {
                double percent = value / this.mMaxValue;
                BrowserActivity.this.setProgressComp((int) (percent * Window.PROGRESS_END)); // from 0 to 10000
            } else {
                BrowserActivity.this.setProgressComp(Window.PROGRESS_INDETERMINATE_ON);
            }
        }

        @Override
        public void setMaxProgress(int value) {
            this.mMaxValue = value;
        }

        @Override
        public void showLoading(String message) {
            BrowserActivity.this.switchToLoadingView();
        }

        @Override
        public void hideLoading() {
            BrowserActivity.this.setProgressComp(Window.PROGRESS_END);
            BrowserActivity.this.switchToPageView();
        }

        @Override
        public void setOnCancelListener(OnCancelListener listener) {
        }

        @Override
        public void showSuccess(File file) {
            BrowserActivity.this.setImage(file);
        }

        @Override
        public void showError(String error) {
            BrowserActivity.this.switchToErrorView(error);
        }

        @Override
        public void showFileExists(File file) {
            // it shouldn't be called, because I checked this situation in the
            // onCreate method
            BrowserActivity.this.setImage(file);
        }

    }

}
