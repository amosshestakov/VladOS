package ru.ilya.alitahome;
import android.app.*;import android.content.*;import android.graphics.Color;import android.graphics.drawable.ColorDrawable;import android.view.*;import android.widget.*;
public class Modal {
 public static class Builder {
 Context c;String title="",message="";String[] items;DialogInterface.OnClickListener itemClick;String[] names=new String[3];DialogInterface.OnClickListener[] clicks=new DialogInterface.OnClickListener[3];
 public Builder(Context c){this.c=c;}public Builder setTitle(String t){title=t;return this;}public Builder setMessage(String t){message=t;return this;}
 public Builder setItems(String[] a,DialogInterface.OnClickListener l){items=a;itemClick=l;return this;}
 public Builder setPositiveButton(String t,DialogInterface.OnClickListener l){names[0]=t;clicks[0]=l;return this;}
 public Builder setNegativeButton(String t,DialogInterface.OnClickListener l){names[1]=t;clicks[1]=l;return this;}
 public Builder setNeutralButton(String t,DialogInterface.OnClickListener l){names[2]=t;clicks[2]=l;return this;}
 public Dialog show(){Dialog d=new Dialog(c);d.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout p=Ui.col(c);int pad=Ui.dp(c,28);p.setPadding(pad,pad,pad,pad);p.setBackground(Ui.bg(c,Ui.CARD));TextView h=Ui.text(c,title,23,Ui.INK);p.addView(h);
 if(!message.isEmpty()){TextView m=Ui.text(c,message,14,Ui.MUTED);m.setPadding(0,Ui.dp(c,16),0,Ui.dp(c,18));p.addView(m);}
 if(items!=null)for(int i=0;i<items.length;i++){final int n=i;Button b=Ui.button(c,items[i],()->{d.dismiss();if(itemClick!=null)itemClick.onClick(d,n);});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,Ui.dp(c,54));lp.topMargin=Ui.dp(c,8);p.addView(b,lp);}
 LinearLayout row=new LinearLayout(c);for(int i=0;i<3;i++)if(names[i]!=null){final int n=i;Button b=Ui.button(c,names[i],()->{d.dismiss();if(clicks[n]!=null)clicks[n].onClick(d,n==0?-1:n==1?-2:-3);});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,Ui.dp(c,52),1);lp.setMargins(0,Ui.dp(c,16),Ui.dp(c,8),0);row.addView(b,lp);}if(row.getChildCount()==0&&items==null)row.addView(Ui.button(c,"Закрыть",()->d.dismiss()));p.addView(row);
 ScrollView scroll=new ScrollView(c);scroll.addView(p);d.setContentView(scroll);Window w=d.getWindow();w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams a=w.getAttributes();a.dimAmount=.72f;w.setAttributes(a);d.show();w.setLayout(Ui.dp(c,600),-2);Ui.full((Activity)c);return d;}
 }
}