package ir.ghadirpartner.nativeapp

import android.content.Context
import android.content.ContextWrapper
import androidx.fragment.app.FragmentActivity

/** Dialogs and themed Compose hosts can wrap their Activity context. */
internal fun Context.fragmentActivity():FragmentActivity? {
    var current:Context=this
    while(current is ContextWrapper) {
        if(current is FragmentActivity)return current
        val next=current.baseContext
        if(next===current)break
        current=next
    }
    return current as? FragmentActivity
}
