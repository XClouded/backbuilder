package com.taobao.url.test;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import com.taobao.taobao.R;

/**
 * Created by lvshan on 14-5-5.
 */
public class UrlTestActivity extends Activity {
    private Button btnVisitor;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnVisitor = (Button) this.findViewById(R.id.btnView);
        btnVisitor.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                doVisit();
            }
        });
    }

   public void doVisit() {
       String testUrl = ((EditText) this.findViewById(R.id.etUrl)).getText().toString();
       Uri uri = Uri.parse(convertUrl(testUrl));
       Intent newIntent = new Intent(Intent.ACTION_VIEW, uri);
       newIntent.setPackage(this.getPackageName());
       try {
           startActivity(newIntent);
       } catch (ActivityNotFoundException e) {
           Toast toast = Toast.makeText(this.getBaseContext(), "url跳转失败:" + testUrl, Toast.LENGTH_LONG);
           toast.show();
           Log.e("test", "Get testurl:" + testUrl + " failed! " + e.getMessage());
       }
   }
       /**
        * 以“HTTP”开头的地址无法搜索到浏览器Activity打开。
        * URL规定，http必须小写，后面域名部分大小写均可。
        * 如果url以http或https开发，统一成小写
        *
        * @param originalUrl
        * @return String
        */
    private String convertUrl(String originalUrl) {
        if (originalUrl == null || originalUrl.trim().length() == 0) {
            return originalUrl;
        }

        String url = originalUrl.toLowerCase();

        if (TextUtils.indexOf(url, "https") == 0) {
            url = "https" + originalUrl.substring(5);
        } else if (TextUtils.indexOf(url, "http") == 0) {
            url = "http" + originalUrl.substring(4);
        } else if (url.startsWith("www")){
            url = "http://" + originalUrl;
        }

        return url;
   }
}
