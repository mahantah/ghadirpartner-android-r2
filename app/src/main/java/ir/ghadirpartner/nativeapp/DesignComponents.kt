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
                        val imageModifier=Modifier.size(22.dp).then(if(onIcon!=null) Modifier.clickable(onClick=onIcon) else Modifier)
                        val svg=when(icon){R.drawable.design_search->"search";R.drawable.design_calendar->"calendar";R.drawable.design_map_pin->"map_pin";R.drawable.design_chevron->"chevron";else->null}
                        if(svg!=null) Box(imageModifier) {
                            val glyphSize=if(svg=="search")19.5554.dp else 22.dp
                            FigmaSvg(svg,null,Modifier.size(glyphSize).align(AbsoluteAlignment.TopLeft),ColorFilter.tint(Muted))
                        } else Image(painterResource(icon),if(onIcon!=null) "نمایش یا پنهان کردن رمز" else null,imageModifier,
                            colorFilter=ColorFilter.tint(Muted))
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
            Box(Modifier.weight(1f).heightIn(min=40.dp).clip(RoundedCornerShape(12.dp))
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
            .background(if(success) {if(AppAppearance.dark)Color(0xFF16382D) else Color(0xFFE9F6EF)} else AppSecondary),
            contentAlignment=Alignment.Center) {Image(painterResource(icon),null,Modifier.size(48.dp))}
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
    Row(Modifier.fillMaxWidth().heightIn(min=84.dp).padding(horizontal=20.dp,vertical=12.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        IconButton(onClick=onBack,modifier=Modifier.size(42.dp)) {
            Image(painterResource(R.drawable.r54_back),"بازگشت",Modifier.size(22.dp),colorFilter=ColorFilter.tint(Ink))
        }
        Column(Modifier.weight(1f)) {
            Text(title,color=Ink,fontSize=22.sp,lineHeight=36.sp,fontWeight=FontWeight.Bold)
            Text("قدیر پارتنر",color=Muted,fontSize=12.sp,lineHeight=20.sp)
        }
    }
}

internal fun orderItemsDescription(order:JSONObject):String = order.arr("items").objects()
    .joinToString(" • ") { "${faNumber(it.i("qty"))} × ${shortProductName(it.s("product"))}" }

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
        Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            if(showIcon)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                Image(painterResource(R.drawable.design_gift),null,Modifier.size(28.dp))
            }
            Text(title,color=Color.White,fontSize=20.sp,lineHeight=33.sp,fontWeight=FontWeight.Bold)
            Text(body,color=Color.White,fontSize=13.sp,lineHeight=22.sp)
            if(button!=null)GhadirButton(button,onClick)
        }
    }
}

/** Figma 169:9118. Android draws the gesture indicator in its native safe area. */
@Composable
internal fun DesignNavigationBar(items:List<NavItem>,selected:String,onSelected:(String)->Unit) {
    Column(Modifier.fillMaxWidth().background(AppSurface).navigationBarsPadding().drawBehind {
        drawLine(Border,androidx.compose.ui.geometry.Offset.Zero,androidx.compose.ui.geometry.Offset(size.width,0f),strokeWidth=1.dp.toPx())
    }) {
        Row(Modifier.fillMaxWidth().padding(start=20.dp,end=20.dp,top=16.dp,bottom=8.dp).selectableGroup(),
            horizontalArrangement=Arrangement.spacedBy(2.dp)) {
            items.forEach {item ->
                val active=item.key==selected
                val asset=when(item.key){"home"->"home";"catalog"->"prices";
                    "new"->"add";"orders"->"orders";else->"profile"}
                Column(Modifier.weight(1f).height(60.dp).clip(RoundedCornerShape(18.dp))
                    .background(if(active)AppSecondary else AppSurface)
                    .selectable(active,role=Role.Tab,onClick={onSelected(item.key)}),
                    horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(2.dp,Alignment.CenterVertically)) {
                    FigmaSvg(asset,null,Modifier.size(22.dp),tint=ColorFilter.tint(if(active)Orange else Muted))
                    Text(item.label,color=if(active)Orange else Muted,fontSize=10.sp,lineHeight=16.sp,
                        fontWeight=if(active)FontWeight.Bold else FontWeight.Normal,maxLines=1)
                }
            }
        }
    }
}
