package com.mine.personnum;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.BottomNavigationView;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.support.v7.widget.Toolbar;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private CoordinatorLayout coordinatorLayout;

    private AppBarLayout appBarLayout;

    private RelativeLayout relativeLayout;

    private ImageView originalImageView;

    private TextView contentText;

    private NavigationView navigationView;

    private DrawerLayout mDrawerLayout;

    private ProgressBar progressBar;

    public BottomNavigationView bottomNavigationView;

    private static final int STORAGE_PERMISSION = 1;

    private static final int TAKE_PHOTO = 1;
    private static final int CHOOSE_PHOTO = 2;

    public static final int NEARBY_VIEW = 1;
    public static final int DISTANT_VIEW = 2;

    public static int lastSelectedNavItem;

    private Uri imageUrl;

    public static int queryMethod;   // NEARBY_VIEW or DISTANT_VIEW

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mDrawerLayout = findViewById(R.id.draw_layout);
        coordinatorLayout = findViewById(R.id.coordinatorLayout);
        appBarLayout = findViewById(R.id.appBar);
        relativeLayout = findViewById(R.id.content_layout);
        originalImageView = findViewById(R.id.original_image_view);
        contentText = findViewById(R.id.content_text);
        progressBar = findViewById(R.id.progressBar);

        // Show ic_menu
        ActionBar actionBar = getSupportActionBar();
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeAsUpIndicator(R.drawable.ic_menu);
        }

        // BottomNavigationView
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnNavigationItemSelectedListener(menuItem -> {
            switch (menuItem.getItemId()) {
                case R.id.nav_nearby:
                    lastSelectedNavItem = R.id.nav_nearby;
                    queryMethod = NEARBY_VIEW;
                    toolbar.setTitle("近景");
                    break;
                case R.id.nav_distant:
                    lastSelectedNavItem = R.id.nav_distant;
                    queryMethod = DISTANT_VIEW;
                    toolbar.setTitle("远景");
                    break;
                case R.id.nav_more:
                    Intent intent = new Intent(MainActivity.this, MoreActivity.class);
                    startActivity(intent);
                    break;
            }
            return true;
        });

        // Enable NavigationView
        navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(menuItem -> {
            switch (menuItem.getItemId()) {
                case R.id.nav_nearby:
                    bottomNavigationView.setSelectedItemId(R.id.nav_nearby);
                    break;
                case R.id.nav_distant:
                    bottomNavigationView.setSelectedItemId(R.id.nav_distant);
                    break;
                case R.id.nav_more:
                    bottomNavigationView.setSelectedItemId(R.id.nav_more);
                    break;
            }
            mDrawerLayout.closeDrawers();
            return true;
        });

        contentText.setText("请选择图片");

        // ProgressBar
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.GONE);

        bottomNavigationView.setSelectedItemId(R.id.nav_nearby);

        checkUpdate();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        bottomNavigationView.setSelectedItemId(lastSelectedNavItem);
    }

    public void getPhotoButtonOnClick(View view) {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setIcon(R.drawable.ic_photo_camera_blue_700_24dp);
        builder.setTitle("\"拍照\"或\"选择照片\"");
        // 指定下拉列表的显示数据
        final String[] ch = {"拍照", "选择照片", "取消"};
        // 设置一个下拉的列表选择项
        builder.setItems(ch, (dialog, which) -> {
            switch (which) {
                case 0:
                    takePhoto();
                    break;
                case 1:
                    choosePhoto();
                    break;
                default:
                    break;
            }
        });
        builder.show();

    }

    public void takePhoto() {
        // 创建 File 对象，用来储存拍摄后的照片
        File outputImage = new File(getExternalCacheDir(), "output_image.jpg");
        try {
            if (outputImage.exists()) {
                outputImage.delete();
            }
            outputImage.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 打开创建的文件资源
        if (Build.VERSION.SDK_INT >= 24) {
            imageUrl = FileProvider.getUriForFile(MainActivity.this,
                    "com.mine.personnum.fileprovider", outputImage);
        } else {
            imageUrl = Uri.fromFile(outputImage);
        }

        // 启动相机程序
        Intent intent = new Intent("android.media.action.IMAGE_CAPTURE");
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUrl);
        startActivityForResult(intent, TAKE_PHOTO);
    }

    public void choosePhoto() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION);
        } else {
            openAlbum();
        }
    }

    public void openAlbum() {
        // 打开相册
        Intent intent = new Intent("android.intent.action.GET_CONTENT");
        intent.setType("image/*");
        startActivityForResult(intent, CHOOSE_PHOTO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        switch (requestCode) {
            case TAKE_PHOTO:
                if (resultCode == RESULT_OK) {
                    // 拍照成功,请求获取人数并显示图片
                    try {
                        Bitmap bitmap = BitmapFactory.decodeStream(
                                getContentResolver().openInputStream(imageUrl));
                        Glide.with(this).load(bitmap).into(originalImageView);
                        queryPersonNum(bitmap);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case CHOOSE_PHOTO:
                if (resultCode == RESULT_OK) {
                    handleImage(data);
                }
            default:
        }
    }

    private void handleImage(Intent data) {
        String imagePath = null;
        Uri uri = data.getData();

        if (DocumentsContract.isDocumentUri(this, uri)) {
            // 如果 document 对类型是 Uri，则通过 document id 处理
            String docId = DocumentsContract.getDocumentId(uri);
            if ("com.android.providers.media.documents".equals(uri.getAuthority())) {
                String id = docId.split(":")[1];
                String selection = MediaStore.Images.Media._ID + "=" + id;
                imagePath = getImagePath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, selection);
            } else if ("com.android.provider.downloads.documents".equals(uri.getAuthority())) {
                Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.valueOf(docId));
                imagePath = getImagePath(contentUri, null);
            }
        } else if ("content".equalsIgnoreCase(uri.getScheme())) {
            // 如果是 content 类型的 Uri，使用普通方法处理
            imagePath = getImagePath(uri, null);
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            // 如果是 file 类型的 Uri，直接获取图片路径就行
            imagePath = uri.getPath();
        }

        Log.d(TAG, "handleImage: imgagePath: " + imagePath);
        // 请求获取人数并显示图片
        Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
        Glide.with(this).load(bitmap).into(originalImageView);
        queryPersonNum(bitmap);

    }

    private String getImagePath(Uri uri, String selection) {
        String path = null;
        // 通过 Uri 和 selection 来获取真实的图片路径
        Cursor cursor = getContentResolver().query(uri, null, selection, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
            }
            cursor.close();
        }
        return path;
    }

    private void queryPersonNum(Bitmap bitmap) {
        if (bitmap != null) {
            progressBar.setVisibility(View.VISIBLE);
            contentText.setText("人数统计中，请稍后...");

            String baseUrl;
            switch (queryMethod) {
                case NEARBY_VIEW:
                    baseUrl = "https://aip.baidubce.com/rest/2.0/image-classify/v1/body_attr";
                    break;
                case DISTANT_VIEW:
                    baseUrl = "https://aip.baidubce.com/rest/2.0/image-classify/v1/body_num";
                    break;
                default:
                    return;
            }
            Log.d(TAG, "queryPersonNum: baseUrl: " + baseUrl);

            String accessToken = getAccessToken();
            String imageBase64 = imageToBase64(bitmap);

            OkHttpClient client = new OkHttpClient();
            RequestBody requestBody = new FormBody.Builder()
                    .add("image", imageBase64)
                    // .add("show", "true")
                    .build();
            Request request = new Request.Builder()
                    .url(baseUrl + "?access_token=" + accessToken)
                    .post(requestBody)
                    .addHeader("Content-Type", "application/x-www-form-urlencoded")
                    .build();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    e.printStackTrace();
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        contentText.setText("请选择图片");
                    });
                    Snackbar snackbar = Snackbar.make(relativeLayout, "获取人数失败", Snackbar.LENGTH_LONG)
                            .setAction("重试", (v -> {
                                queryPersonNum(bitmap);
                            }));
                    CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams)
                            snackbar.getView().getLayoutParams();
                    params.setAnchorId(R.id.bottom_navigation); //id of the bottom navigation view
                    params.gravity = Gravity.TOP;
                    params.anchorGravity = Gravity.TOP;
                    snackbar.getView().setLayoutParams(params);
                    snackbar.show();
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        contentText.setText("请选择图片");
                    });
                    final String responseText = response.body().string();
                    JSONObject jsonObject = null;
                    try {
                        jsonObject = new JSONObject(responseText);
                        int personNum = jsonObject.getInt("person_num");
                        // String imageStr = jsonObject.getString("image");
                        // Bitmap img = base64ToImage(imageStr);
                        runOnUiThread(() -> {
                            contentText.setText("人数: " + personNum);
                            // Glide.with(MainActivity.this).load(img).into(originalImageView);
                        });
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Snackbar snackbar = Snackbar.make(relativeLayout, "获取人数失败", Snackbar.LENGTH_LONG)
                                .setAction("重试", (v -> {
                                    queryPersonNum(bitmap);
                                }));
                        CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams)
                                snackbar.getView().getLayoutParams();
                        params.setAnchorId(R.id.bottom_navigation); //id of the bottom navigation view
                        params.gravity = Gravity.TOP;
                        params.anchorGravity = Gravity.TOP;
                        snackbar.getView().setLayoutParams(params);
                        snackbar.show();
                    }
                }
            });
        }
    }

    /**
     * 获取 Baidu Api Access Token
     */
    private String getAccessToken() {
        // TODO: 每月手动更新 Access Token
        // Update: Fri, 16 Aug 2019 13:30:30 GMT
        return "24.b1d51762b984d0363fb780fc67c074e3.2592000.1568554230.282335-16727034";
    }

    /**
     * 检查更新是否到期（30天）
     */
    private void checkUpdate() {
        // 最大时间差: 30*24*60*60 (s)
        long maxDiff = 2592000;
        // 获取更新时间戳(秒)
        long update = Long.valueOf(getString(R.string.update));
        // 获取当前时间戳(秒)
        long current = System.currentTimeMillis() / 1000;

        Log.d(TAG, "checkUpdate: update: " + update);
        Log.d(TAG, "checkUpdate: current: " + current);

        if (current - update >= maxDiff) {
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setIcon(R.drawable.icon);
            builder.setTitle("需要版本更新");
            builder.setMessage("抱歉，当前 personum 版本过低(v" +
                    getString(R.string.version) +
                    ")，请更新至最新版本。\n否则，您可能无法正常使用 personum。");
            // 指定下拉列表的显示数据
            final String[] ch = {"确定"};
            // 设置一个下拉的列表选择项
            builder.setItems(ch, (dialog, which) -> {
                switch (which) {
                    case 0:
                        finish();
                        break;
                    default:
                        break;
                }
            });
            builder.show();
        }

    }


    /**
     * 将 Bitmap 转换为 Base64 编码的字符串
     */
    public static String imageToBase64(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        //读取图片到ByteArrayOutputStream
        bitmap.compress(Bitmap.CompressFormat.JPEG, 40, baos);  //参数如果为100那么就不压缩
        byte[] bytes = baos.toByteArray();

        String strbm = Base64.encodeToString(bytes,Base64.DEFAULT);

        return strbm;
    }

    /**
     * 将Base64编码转换为 Bitmap
     */
    public static Bitmap base64ToImage(String base64Str) {
        byte [] input = Base64.decode(base64Str, Base64.DEFAULT);
        Bitmap bitmap = BitmapFactory.decodeByteArray(input, 0, input.length);
        return bitmap;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                mDrawerLayout.openDrawer(GravityCompat.START);
                break;
            default:
                break;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case STORAGE_PERMISSION:
                if (grantResults.length > 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openAlbum();
                } else {
                    Toast.makeText(this, "You denied the permission!", Toast.LENGTH_SHORT).show();
                }
                break;
            default:
        }
    }
}
