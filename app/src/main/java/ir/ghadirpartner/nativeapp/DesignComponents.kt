package ir.ghadirpartner.nativeapp

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

/** Shared geometry from Figma page 100:573. System bars remain native Android UI. */
@Composable
internal fun DesignField(
    label: String, value: String, onChange: (String) -> Unit,
    placeholder: String = "", icon: Int? = null, onIcon: (() -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    transformation: VisualTransformation = VisualTransformation.None,
    enabled: Boolean = true, modifier: Modifier = Modifier, singleLine: Boolean = true
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if(label.isNotBlank()) Text(label, color=Muted, fontSize=12.sp, lineHeight=20.sp,
            modifier=Modifier.fillMaxWidth(), textAlign=TextAlign.Start)
        BasicTextField(value=value, onValueChange=onChange, enabled=enabled,
            singleLine=singleLine, visualTransformation=transformation,
            keyboardOptions=KeyboardOptions(keyboardType=keyboardType),
            textStyle=MaterialTheme.typography.bodyMedium.copy(color=Ink, fontSize=14.sp, lineHeight=23.sp, textAlign=TextAlign.Start),
            cursorBrush=SolidColor(Orange),
            modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).semantics { contentDescription=label },
            decorationBox={inner ->
                Row(Modifier.fillMaxWidth().heightIn(min=52.dp).clip(RoundedCornerShape(16.dp))
                    .background(AppSurface).border(1.dp,Border,RoundedCornerShape(16.dp))
                    .padding(horizontal=14.dp,vertical=12.dp), verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    if(icon!=null) {
                        val svg=when(icon){R.drawable.r54_phone->"phone";R.drawable.r54_eye->"eye";R.drawable.design_search->"search";R.drawable.design_calendar->"calendar";R.drawable.design_map_pin->"map_pin";R.drawable.design_chevron->"chevron";else->null}
                        val glyph:@Composable ()->Unit = {
                            if(svg!=null) FigmaSvg(svg,null,Modifier.size(28.dp),ColorFilter.tint(Muted))
                            else Image(painterResource(icon),null,Modifier.size(28.dp),colorFilter=ColorFilter.tint(Muted))
                        }
                        if(onIcon!=null) IconButton(onClick=onIcon,modifier=Modifier.size(48.dp).semantics {contentDescription="نمایش یا پنهان کردن رمز"}) {glyph()}
                        else Box(Modifier.size(28.dp),contentAlignment=Alignment.Center) {glyph()}

                    }
                    Box(Modifier.weight(1f)) {
                        if(value.isBlank()) Text(placeholder,color=Muted,fontSize=14.sp)
                        inner()
                    }
                }
            })
    }
}

@Composable
internal fun DesignChoices(choices: List<Pair<String,String>>, selected: String,
    onSelect: (String)->Unit) {
    Row(Modifier.fillMaxWidth().selectableGroup(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        choices.forEach { (key,label) ->
            val active=key==selected
            Box(Modifier.weight(1f).heightIn(min=48.dp).clip(RoundedCornerShape(12.dp))
                .background(if(active)Orange else AppSurface)
                .border(1.dp,if(active)Orange else Border,RoundedCornerShape(12.dp))
                .selectable(active,role=Role.Tab,onClick={onSelect(key)}).padding(6.dp),contentAlignment=Alignment.Center) {
                Text(label,color=if(active)Navy else Muted,fontSize=12.sp,lineHeight=18.sp,
                    fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
            }
        }
    }
}

@Composable
internal fun DesignNotice(text: String) {
    Surface(color=AppSecondary,shape=RoundedCornerShape(24.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Border)) {
        Text(text,color=Ink,fontSize=12.sp,lineHeight=20.sp,modifier=Modifier.fillMaxWidth().padding(16.dp))
    }
}

@Composable
internal fun DesignState(title:String,body:String,icon:Int,success:Boolean=false) {
    Column(Modifier.fillMaxWidth().padding(top=48.dp,bottom=28.dp),
        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Box(Modifier.size(104.dp).clip(RoundedCornerShape(32.dp))
            .background(if(icon in setOf(R.drawable.design_offline,R.drawable.design_fingerprint_error,R.drawable.design_download_error)) {
                if(AppAppearance.dark)Color(0xFF3D2530) else Color(0xFFFAECEC)
            } else AppSecondary),
            contentAlignment=Alignment.Center) {DesignGlyph(icon,null,Modifier.size(48.dp))}
        Text(title,color=Ink,fontSize=24.sp,lineHeight=40.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
        Text(body,color=Muted,fontSize=14.sp,lineHeight=23.sp,textAlign=TextAlign.Center)
    }
}

@Composable
internal fun DesignOtp(value:String,onChange:(String)->Unit,label:String="کد یک‌بارمصرف") {
    Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text(label,color=Muted,fontSize=12.sp,modifier=Modifier.fillMaxWidth())
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            BasicTextField(value=value,onValueChange={onChange(asciiDigits(it).filter(Char::isDigit).take(6))},
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,
                textStyle=TextStyle(color=Color.Transparent),cursorBrush=SolidColor(Color.Transparent),
                modifier=Modifier.fillMaxWidth().semantics {contentDescription="کد شش‌رقمی"},
                decorationBox={inner ->
                    Box {
                        // Keep the real editable field in the composition for focus, paste and accessibility.
                        Box(Modifier.matchParentSize()){inner()}
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            repeat(6) {i -> Box(Modifier.weight(1f).height(56.dp)
                                .clip(RoundedCornerShape(12.dp)).background(AppSurface)
                                .border(1.dp,if(i==value.length.coerceAtMost(5))Orange else Border,RoundedCornerShape(12.dp)),
                                contentAlignment=Alignment.Center) {
                                Text(value.getOrNull(i)?.toString()?.let(::faDigits)?:"—",color=Ink,fontSize=22.sp)
                            }}
                        }
                    }
                })
        }
    }
}

@Composable
internal fun PortalBackHeader(title:String,onBack:()->Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().heightIn(min=84.dp).padding(horizontal=20.dp,vertical=12.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        IconButton(onClick=onBack,modifier=Modifier.size(48.dp)) {
            FigmaSvg("back","بازگشت",Modifier.size(28.dp),tint=ColorFilter.tint(Ink))
        }
        Column(Modifier.weight(1f)) {
            Text(title,color=Ink,fontSize=22.sp,lineHeight=36.sp,fontWeight=FontWeight.Bold)
            Text("قدیر پارتنر",color=Muted,fontSize=12.sp,lineHeight=20.sp)
        }
    }
}

internal fun orderItemsDescription(order:JSONObject):String = order.arr("items").objects()
    .joinToString(" • ") { "${shortProductName(it.s("product"))}؛ تعداد: ${faNumber(it.i("qty"))} دستگاه" }

@Composable
internal fun DesignOrderCard(order:JSONObject,latest:Boolean=false,onClick:()->Unit) {
    GlassSurface(shape=RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(latest)Text("آخرین سفارش",color=Muted,fontSize=12.sp)
            if(latest)Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(faDigits(order.s("number")),color=Ink,fontSize=18.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                StatusPill(order.s("status"))
            }else {
                StatusPill(order.s("status"))
                Text(faDigits(order.s("number")),color=Ink,fontSize=18.sp,fontWeight=FontWeight.Bold)
            }
            Text(orderItemsDescription(order)+" • "+formatDateFa(order.s("created_at")),color=Muted,fontSize=14.sp,lineHeight=23.sp)
            GhadirButton(if(latest)"پیگیری سفارش" else "جزئیات سفارش",onClick,secondary=true)
        }
    }
}

@Composable
internal fun DesignOfferHero(title:String,body:String,button:String?=null,showIcon:Boolean=false,onClick:()->Unit={}) {
    Box(Modifier.fillMaxWidth().heightIn(min=190.dp).clip(RoundedCornerShape(24.dp))) {
        Image(painterResource(R.drawable.figma_mix_offer),null,Modifier.matchParentSize(),contentScale=ContentScale.Crop)
        Box(Modifier.matchParentSize().background(Color(0xFF0A1424).copy(alpha=.45f)))
        Column(Modifier.align(Alignment.Center).padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.spacedBy(10.dp)) {
            if(showIcon)DesignGlyph(R.drawable.design_gift,null,Modifier.size(28.dp))
            Text(title,color=Color.White,fontSize=20.sp,lineHeight=33.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
            Text(body,color=Color.White,fontSize=13.sp,lineHeight=22.sp,textAlign=TextAlign.Center)
            if(button!=null)Button(onClick=onClick,shape=RoundedCornerShape(16.dp),
                colors=ButtonDefaults.buttonColors(containerColor=Orange,contentColor=Navy)) {
                Text(button,fontWeight=FontWeight.Bold)
            }
        }
    }
}

/** A rail keeps top-level destinations within reach on tablets and wide windows. */
@Composable
internal fun AppNavigationRail(items:List<NavItem>,selected:String,onSelected:(String)->Unit) {
    NavigationRail(modifier=Modifier.fillMaxHeight(),containerColor=AppSurface) {
        items.forEach { item ->
            NavigationRailItem(selected=item.key==selected,onClick={onSelected(item.key)},
                icon={Icon(item.icon,null)},label={Text(item.label)},
                colors=NavigationRailItemDefaults.colors(selectedIconColor=Ink,selectedTextColor=Ink,indicatorColor=Orange))
        }
    }
}

@Composable
internal fun CheckoutOptionCard(title:String,summary:String,icon:androidx.compose.ui.graphics.vector.ImageVector,modifier:Modifier=Modifier,selected:Boolean=false,onClick:()->Unit) {
    Surface(modifier=modifier.heightIn(min=100.dp).clip(RoundedCornerShape(18.dp))
        .clickable(role=Role.Button,onClick=onClick).semantics {contentDescription="تنظیم "+title},
        shape=RoundedCornerShape(18.dp),color=if(selected)AppSecondary else AppSurface,
        border=androidx.compose.foundation.BorderStroke(1.dp,if(selected)Orange.copy(alpha=.55f) else Border)) {
        Column(Modifier.fillMaxWidth().padding(horizontal=6.dp,vertical=12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Icon(icon,null,tint=Orange,modifier=Modifier.size(24.dp))
            Text(title,color=Ink,fontSize=12.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
            Text(summary,color=Muted,fontSize=10.sp,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun ProfileServiceTile(title:String,subtitle:String,icon:androidx.compose.ui.graphics.vector.ImageVector,modifier:Modifier=Modifier,wide:Boolean=false,onClick:()->Unit) {
    Surface(modifier=modifier.clip(RoundedCornerShape(22.dp)).clickable(role=Role.Button,onClick=onClick),
        shape=RoundedCornerShape(22.dp),color=AppSurface,border=androidx.compose.foundation.BorderStroke(1.dp,Border)) {
        Column(Modifier.fillMaxWidth().heightIn(min=if(wide)100.dp else 126.dp).padding(14.dp),
            horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp,Alignment.CenterVertically)) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(AppSecondary),contentAlignment=Alignment.Center){Icon(icon,null,tint=Orange,modifier=Modifier.size(25.dp))}
            Text(title,color=Ink,fontWeight=FontWeight.Bold,fontSize=14.sp,lineHeight=22.sp,textAlign=TextAlign.Center)
            Text(subtitle,color=Muted,fontSize=11.sp,lineHeight=18.sp,textAlign=TextAlign.Center)
        }
    }
}
