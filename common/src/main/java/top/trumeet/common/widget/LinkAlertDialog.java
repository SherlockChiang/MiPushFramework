package top.trumeet.common.widget;

import android.content.Context;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

/**
 * Created by Trumeet on 2017/12/30.
 */

public class LinkAlertDialog extends AlertDialog {
    protected LinkAlertDialog(Context context) {
        super(context);
    }

    protected LinkAlertDialog(Context context, boolean cancelable, OnCancelListener cancelListener) {
        super(context, cancelable, cancelListener);
    }

    protected LinkAlertDialog(Context context, int themeResId) {
        super(context, themeResId);
    }

    public static class Builder extends AlertDialog.Builder {

        public Builder(Context context) {
            super(context);
        }

        public Builder(Context context, int themeResId) {
            super(context, themeResId);
        }

        /**
         * Set the message to display.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        @Override
        public Builder setMessage(CharSequence message) {
            TextView textView = new TextView(getContext());
            int padding = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    24,
                    getContext().getResources().getDisplayMetrics());
            textView.setPadding(padding, padding, padding, padding);
            textView.setMovementMethod(LinkMovementMethod.getInstance());
            textView.setText(message);
            setView(textView);
            return this;
        }
    }
}
