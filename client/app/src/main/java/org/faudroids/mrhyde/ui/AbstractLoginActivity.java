package org.faudroids.mrhyde.ui;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.StringRes;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.common.base.Optional;

import org.faudroids.mrhyde.R;
import org.faudroids.mrhyde.auth.Account;
import org.faudroids.mrhyde.ui.utils.AbstractActivity;
import org.faudroids.mrhyde.utils.DefaultErrorAction;
import org.faudroids.mrhyde.utils.DefaultTransformer;
import org.faudroids.mrhyde.utils.ErrorActionBuilder;
import org.faudroids.mrhyde.utils.HideSpinnerAction;

import butterknife.BindView;
import butterknife.ButterKnife;
import rx.Observable;


/**
 * Base class for OAuth style login.
 */
abstract class AbstractLoginActivity<T extends Account> extends AbstractActivity {

  static class NotAuthenticatedException extends Exception {}

  abstract Intent getTargetIntent();
  abstract @StringRes int getActivityTitle();
  abstract String getLoginUrl();
  abstract Optional<String> getCodeFromUrl(String url) throws NotAuthenticatedException;
  abstract Observable<T> getAccountFromCode(String accessCode);

  abstract T getStoredAccount();
  abstract void storeAccount(T account);


  // if true, this activity will simply finished when logged in, instead of going to the repos overview
  public static final String EXTRA_DO_NOT_FORWARD_TO_NEXT_ACTIVITY = "EXTRA_DO_NOT_FORWARD_TO_NEXT_ACTIVITY";

  @BindView(R.id.web_view) WebView loginView;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_login);
    ButterKnife.bind(this);
    setTitle(getActivityTitle());

    // check if logged in
    if (getStoredAccount() != null) {
      onLoginSuccess();
      return;
    }

    // check for interrupted login attempt
    if (savedInstanceState != null) {
      startLogin(savedInstanceState);
    } else {
      startLogin(null);
    }
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    if (loginView != null) loginView.saveState(outState);
    super.onSaveInstanceState(outState);
  }

  @Override
  public void onBackPressed() {
    setResult(RESULT_CANCELED);
    super.onBackPressed();
  }

  private void startLogin(Bundle savedState) {
    loginView.getSettings().setJavaScriptEnabled(true);
    loginView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
    if (savedState != null) {
      loginView.restoreState(savedState);
    } else {
      loginView.loadUrl(getLoginUrl());
    }
    loginView.setWebViewClient(new WebViewClient() {
      @Override
      public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        try {
          Optional<String> code = getCodeFromUrl(url);
          if (!code.isPresent()) return;
          getAccessToken(code.get());
        } catch (NotAuthenticatedException e) {
          onAccessDenied();
        }
      }
    });
  }

  private void getAccessToken(String code) {
    showSpinner();
    compositeSubscription.add(getAccountFromCode(code)
        .compose(new DefaultTransformer<>())
        .subscribe(account -> {
          storeAccount(account);
          onLoginSuccess();
        }, new ErrorActionBuilder()
            .add(new DefaultErrorAction(this, "failed to get token"))
            .add(new HideSpinnerAction(this))
            .build()));
  }

  private void onLoginSuccess() {
    boolean doNotForward = getIntent().getBooleanExtra(EXTRA_DO_NOT_FORWARD_TO_NEXT_ACTIVITY, false);
    if (!doNotForward) {
      startActivity(getTargetIntent());
    }
    setResult(RESULT_OK);
    finish();
  }

  private void onAccessDenied() {
    new MaterialDialog.Builder(this)
        .title(R.string.login_error_title)
        .content(R.string.login_error_message)
        .positiveText(android.R.string.ok)
        .onAny((dialog, which) -> startLogin(null))
        .cancelListener(dialogInterface -> startLogin(null))
        .show();
  }

}
