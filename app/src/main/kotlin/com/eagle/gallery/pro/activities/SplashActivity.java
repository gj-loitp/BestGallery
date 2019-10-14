package com.eagle.gallery.pro.activities;


import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.eagle.gallery.pro.R;
import com.eagle.gallery.pro.utils.MainHandler;


import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        setContentView(R.layout.activity_splash);

        /*startAnim();*/
        MainHandler.getInstance().postDelayed(runnable, 100);
    }


    private Runnable runnable = () -> {
        tryFinish();
    };

    private void tryFinish() {
        if (!isFinishing()) {
            finish();
            overridePendingTransition(R.anim.common_anim_none, R.anim.common_zoom_exit);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        MainHandler.getInstance().removeCallbacks(runnable);
    }

    public static void startActivity(Context context) {
        Intent intent = new Intent(context, SplashActivity.class);
        context.startActivity(intent);
    }
}