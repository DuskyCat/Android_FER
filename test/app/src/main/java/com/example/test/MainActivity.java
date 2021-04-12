package com.example.test;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {
    private static final int FROM_ALBUM = 1;
    private static final int FROM_CAMERA = 2;
    static final int PIXEL_WIDTH = 48;
    Button detect;
    ImageView iv;
    Bitmap bmp;
    String[] label=new String[]{"angry", "disgust", "fear", "happy", "sad", "surprise", "neutral"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        iv = findViewById(R.id.photo);
        // 저장되어있는 이미지 불러오기
        findViewById(R.id.button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent();
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(intent, FROM_ALBUM);
            }
        });

        //카메라로 캡쳐해서 불러오기
        Button photoButton = (Button) this.findViewById(R.id.cambtn);
        photoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int PERMISSIONCheck = ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA);
                if (PERMISSIONCheck == PackageManager.PERMISSION_DENIED){
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA},0);
                }else{
                    Intent cameraintent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    startActivityForResult(cameraintent,FROM_CAMERA);
                }


            }
        });

        //분석버튼
        detect = (Button) findViewById(R.id.detect);
        detect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                detectEmotion();
            }
        });

        //초기화(clear) 버튼
        Button reset = (Button) findViewById(R.id.reset);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                clearStatus();
            }
        });

        detect.setEnabled(false);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // 카메라

        if(resultCode != RESULT_OK){
            return;
        }
        detect.setEnabled(true);


        if (requestCode == FROM_CAMERA) {
            //카메라로부터 사진 불러오기
            bmp = (Bitmap) data.getExtras().get("data");
            iv.setScaleType(ImageView.ScaleType.FIT_XY);
            iv.setImageBitmap(bmp);

        }else if(requestCode == FROM_ALBUM){
            try {
                //앨범에서 사진 불러오기
                InputStream stream = getContentResolver().openInputStream(data.getData());
                bmp = BitmapFactory.decodeStream(stream);
                stream.close();

                iv.setScaleType(ImageView.ScaleType.FIT_XY);
                iv.setImageBitmap(bmp);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }


    }


    private Interpreter getTfliteInterpreter(String modelPath) {
        try {
            return new Interpreter(loadModelFile(MainActivity.this, modelPath));
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // 모델을 읽어오는 함수
    private MappedByteBuffer loadModelFile(Activity activity, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }


    public Bitmap toGrayscale(Bitmap bmpOriginal)
    {
        int width, height;
        height = bmpOriginal.getHeight();
        width = bmpOriginal.getWidth();

        Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmpGrayscale);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        c.drawBitmap(bmpOriginal, 0, 0, paint);
        return bmpGrayscale;
    }


    public Bitmap getResizedBitmap(Bitmap image, int bitmapWidth, int bitmapHeight) {
        return Bitmap.createScaledBitmap(image, bitmapWidth, bitmapHeight, true);
    }

    private void detectEmotion(){

        Bitmap image=bmp;
        Bitmap grayImage = toGrayscale(image);
        Bitmap resizedImage=getResizedBitmap(grayImage,48,48);
        int pixelarray[];

        pixelarray = new int[resizedImage.getWidth()*resizedImage.getHeight()];

        resizedImage.getPixels(pixelarray, 0, resizedImage.getWidth(), 0, 0, resizedImage.getWidth(), resizedImage.getHeight());


        float normalized_pixels [] = new float[pixelarray.length];
        for (int i=0; i < pixelarray.length; i++) {
            int pix = pixelarray[i];
            int b = pix & 0xff;
            normalized_pixels[i] = (float)(b);

        }
        System.out.println(normalized_pixels);
        Log.d("pixel_values",String.valueOf(normalized_pixels));
        String text=null;


        float[][][][] bytes_img = new float[1][48][48][1];

        for(int y = 0; y < 48; y++) {
            for (int x = 0; x < 48; x++) {
                int pixel = resizedImage.getPixel(x, y);
                bytes_img[0][x][y][0] = (pixel & 0xff) / (float) 255;
            }
        }

        System.out.println("create");
        Interpreter tf_lite = getTfliteInterpreter("converted_model.tflite");

        float[][] output = new float[1][7];
        tf_lite.run(bytes_img, output);
        Log.d("predict", Arrays.toString(output));

        int[] id_array = {R.id.result_0, R.id.result_1, R.id.result_2, R.id.result_3, R.id.result_4,
                R.id.result_5, R.id.result_6};

        for(int i = 0; i < 7; i++) {
            TextView tv = findViewById(id_array[i]);
            tv.setText(String.format("%s : %.5f",label[i], output[0][i]));
            System.out.println(output[0][i]);
        }
    }

    private void clearStatus(){
        detect.setEnabled(false);
        this.iv.setImageResource(0);

        int[] id_array = {R.id.result_0, R.id.result_1, R.id.result_2, R.id.result_3, R.id.result_4,
                R.id.result_5, R.id.result_6};

        for(int i = 0; i < 7; i++) {
            TextView tv = findViewById(id_array[i]);
            tv.setText(String.format("result %d",i));

        }

    }

}
