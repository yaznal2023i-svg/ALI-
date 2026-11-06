package ly.altoud.field;

import android.app.Activity;
import android.app.PrintManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.ValueCallback;
import android.widget.Toast;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
  private WebView web;
  private ValueCallback<Uri[]> fileCallback;
  private static final int FILE_PICKER = 501;
  private static final int FILE_SAVE = 502;
  private byte[] pendingExport;

  @Override public void onCreate(Bundle saved) {
    super.onCreate(saved);
    WebViewAssetLoader loader = new WebViewAssetLoader.Builder().addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this)).build();
    web = new WebView(this);
    setContentView(web);
    WebSettings settings = web.getSettings();
    settings.setJavaScriptEnabled(true);
    settings.setDomStorageEnabled(true);
    settings.setAllowFileAccess(false);
    settings.setAllowContentAccess(false);
    web.addJavascriptInterface(new Bridge(), "AndroidField");
    web.setWebViewClient(new WebViewClientCompat() {
      @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        return loader.shouldInterceptRequest(request.getUrl());
      }
    });
    web.setWebChromeClient(new WebChromeClient() {
      @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
        fileCallback = callback;
        try { startActivityForResult(params.createIntent(), FILE_PICKER); return true; }
        catch (Exception ex) { fileCallback=null; callback.onReceiveValue(null); return false; }
      }
    });
    web.loadUrl("https://appassets.androidplatform.net/assets/index.html");
  }
  class Bridge {
    @JavascriptInterface public void saveFile(String name, String content, String mime) {
      runOnUiThread(() -> {
        pendingExport = ("\uFEFF"+content).getBytes(StandardCharsets.UTF_8);
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_TITLE, name);
        startActivityForResult(i, FILE_SAVE);
      });
    }
    @JavascriptInterface public void printPage() {
      runOnUiThread(() -> {
        PrintManager pm = (PrintManager)getSystemService(Context.PRINT_SERVICE);
        pm.print("ALTOUD Field Report", web.createPrintDocumentAdapter("ALTOUD Field Report"), null);
      });
    }
    @JavascriptInterface public void shareText(String text) {
      runOnUiThread(() -> {
        Intent i=new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,text);
        startActivity(Intent.createChooser(i,"مشاركة التقرير"));
      });
    }
  }
  @Override protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request,result,data);
    if (request==FILE_PICKER) {
      if (fileCallback!=null) { fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result,data)); fileCallback=null; }
    } else if (request==FILE_SAVE && result==RESULT_OK && data!=null && pendingExport!=null) {
      try (OutputStream os = getContentResolver().openOutputStream(data.getData())) {
        os.write(pendingExport); Toast.makeText(this,"تم حفظ الملف",Toast.LENGTH_SHORT).show();
      } catch (Exception e) { Toast.makeText(this,"تعذر حفظ الملف",Toast.LENGTH_LONG).show(); }
      pendingExport=null;
    }
  }
  @Override public void onBackPressed() {
    if (web.canGoBack()) web.goBack(); else super.onBackPressed();
  }
}
