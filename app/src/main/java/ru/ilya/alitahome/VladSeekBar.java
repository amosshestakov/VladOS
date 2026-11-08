package ru.ilya.alitahome;
import android.content.Context;import android.graphics.*;import android.widget.SeekBar;
public class VladSeekBar extends SeekBar {
 private final Paint paint=new Paint(3);
 public VladSeekBar(Context c){super(c);setPadding(Ui.dp(c,10),0,Ui.dp(c,10),0);setMinimumHeight(Ui.dp(c,46));setBackgroundColor(Color.TRANSPARENT);setSplitTrack(false);}
 @Override protected synchronized void onDraw(Canvas canvas){float left=getPaddingLeft(),right=getWidth()-getPaddingRight(),y=getHeight()/2f;paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeWidth(Ui.dp(getContext(),3));paint.setColor(VladDesign.LINE);canvas.drawLine(left,y,right,y,paint);float x=left+(right-left)*getProgress()/Math.max(1f,getMax());paint.setColor(VladDesign.VIOLET);canvas.drawLine(left,y,x,y,paint);paint.setColor(isFocused()?VladDesign.TEXT:VladDesign.VIOLET_HI);canvas.drawCircle(x,y,Ui.dp(getContext(),isFocused()?7:5),paint);}
}