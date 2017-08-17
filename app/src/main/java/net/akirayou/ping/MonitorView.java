package net.akirayou.ping;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * Created by akira on 17/08/17.
 */

public class MonitorView extends View {
    public float maxValue=1;
    public float [] data={0,0.5f,1};
    public MonitorView(Context context, AttributeSet attrs) {

        super(context, attrs);
        Paint paint = new Paint();

    }

    @Override
    protected void onDraw(Canvas canvas) {
        int h=canvas.getHeight();
        int w=canvas.getWidth();

        // 背景、半透明
        canvas.drawColor(Color.argb(255, 0, 0, 0));

        Paint paint=new Paint();
        // 矩形
        paint.setColor(Color.argb(255,0, 100, 250));
        paint.setStyle(Paint.Style.FILL);
        // (x1,y1,x2,y2,paint) 左上の座標(x1,y1), 右下の座標(x2,y2)
        int len=data.length;
        for(int i=0;i<len;i++) {
            int dh= (int)((1.0f-data[i]/maxValue)*h);
            int x1=i*w/len;
            int x2=(i+1)*w/len;

            canvas.drawRect(x1, dh,x2, h, paint);
        }
    }
}
