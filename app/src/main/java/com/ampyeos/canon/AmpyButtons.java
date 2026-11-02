package com.ampyeos.canon;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.widget.Button;

/**
 * A single semantic button palette throughout AmpyEOS.
 *
 * TOGGLE ON      = bright ice-mint, dark text
 * TOGGLE OFF     = dark charcoal, light text, explicit OFF label (still clickable)
 * ONE-SHOT ACTION= medium blue, white text
 * DISABLED       = very dark slate, muted text (not clickable)
 *
 * Every toggle exposes its state via text and accessibility, not color alone.
 */
public final class AmpyButtons {
    private static final int TOGGLE_ON=0xffd8fff3;
    private static final int TOGGLE_OFF=0xff202d3b;
    private static final int ACTION=0xff267cba;
    private static final int DISABLED=0xff101820;
    private static final int TEXT_ON=0xff082b27;
    private static final int TEXT_OFF=0xffd6e4e8;
    private static final int TEXT_ACTION=Color.WHITE;
    private static final int TEXT_DISABLED=0xff70818a;
    private static final int OUTLINE_OFF=0xff536876;
    private static final int OUTLINE_ACTION=0xff4ab9ee;
    private static final int OUTLINE_ON=0xff9fe9d3;

    private AmpyButtons(){}

    public static Button action(Context context,String label){
        Button b=new Button(context);
        b.setText(label);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setBackground(selector(context,false));
        b.setTextColor(textColors(false));
        b.setMinHeight(dp(context,48));
        b.setMinimumHeight(dp(context,48));
        b.setPadding(dp(context,12),dp(context,7),dp(context,12),dp(context,7));
        return b;
    }
    public static Button toggle(Context context,String label,boolean enabled){
        Button b=action(context,label);
        b.setBackground(selector(context,true));
        b.setTextColor(textColors(true));
        setToggle(b,enabled);
        return b;
    }
    public static void setToggle(Button b,boolean on){
        b.setSelected(on);
        Object tag=b.getTag();
        String label=tag instanceof String?(String)tag:b.getText().toString();
        if(!(tag instanceof String))b.setTag(label);
        b.setText(label+(on?" · ON":" · OFF"));
        b.setContentDescription(label+", "+(on?"attivo":"disattivato"));
        b.setStateDescription(on?"Attivo":"Disattivato");
    }

    private static StateListDrawable selector(Context ctx,boolean toggle){
        StateListDrawable state=new StateListDrawable();
        state.addState(new int[]{-android.R.attr.state_enabled},shape(ctx,DISABLED,0xff243039));
        if(toggle){
            state.addState(new int[]{android.R.attr.state_selected},shape(ctx,TOGGLE_ON,OUTLINE_ON));
            state.addState(new int[]{},shape(ctx,TOGGLE_OFF,OUTLINE_OFF));
        }else{
            state.addState(new int[]{android.R.attr.state_pressed},shape(ctx,0xff165e8e,OUTLINE_ACTION));
            state.addState(new int[]{},shape(ctx,ACTION,OUTLINE_ACTION));
        }
        return state;
    }
    private static ColorStateList textColors(boolean toggle){
        int[][] states={
            new int[]{-android.R.attr.state_enabled},
            new int[]{android.R.attr.state_selected},
            new int[]{}
        };
        int[] colors={
            TEXT_DISABLED,
            toggle?TEXT_ON:TEXT_ACTION,
            toggle?TEXT_OFF:TEXT_ACTION
        };
        return new ColorStateList(states,colors);
    }
    private static GradientDrawable shape(Context context,int fill,int stroke){
        GradientDrawable d=new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(context,13));
        d.setStroke(dp(context,1),stroke);
        return d;
    }
    private static int dp(Context context,int value){
        return Math.round(context.getResources().getDisplayMetrics().density*value);
    }
}
