package com.autumn.s44tool

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

enum class AppTab(val title:String,val icon:String) {
    EXACT("精确计算","∑"),
    PEAK("巅峰反推","↗"),
    MATCHES("45场对局","45"),
    FILES("导入导出","⇅"),
    RULES("规则审计","✓"),
    STYLE("外观","◐")
}

data class PadTarget(
    val id:String,
    val label:String,
    val dataMode:Boolean=false,
    val qqMode:Boolean=false
)

private val PowerRed = Color(0xFFE34858)
private val CoeffGold = Color(0xFFD89A00)
private val PeakBlue = Color(0xFF3478E5)
private val PerfPurple = Color(0xFF8157D8)
private val MatchCyan = Color(0xFF3A9DA6)
private val GoodGreen = Color(0xFF48B685)
private val WarnOrange = Color(0xFFE3A34B)
private val AuthorGold = Color(0xFFC89B25)

@Composable
fun S44NativeApp(
    controller: AppController,
    music: MusicEngine,
    feedback: UiFeedback
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(AppTab.EXACT) }
    var padTarget by remember { mutableStateOf<PadTarget?>(null) }
    var padCaret by remember { mutableIntStateOf(0) }
    var primedId by remember { mutableStateOf<String?>(null) }
    var primeUntil by remember { mutableLongStateOf(0L) }
    var showNotice by rememberSaveable { mutableStateOf(true) }
    var showMusicPanel by remember { mutableStateOf(false) }
    var showPptMemoryChoice by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingPpt by remember { mutableStateOf<ByteArray?>(null) }

    val bgBitmap = rememberUriBitmap(controller.customBackgroundUri)

    fun postMessage(text:String) {
        message = text
    }

    fun openPad(target:PadTarget, value:String) {
        padTarget = target
        padCaret = value.length
        primedId = null
        feedback.tap()
    }

    fun tapInput(target:PadTarget, value:String) {
        val now=System.currentTimeMillis()
        if (primedId==target.id && now<primeUntil) {
            openPad(target,value)
        } else {
            primedId=target.id
            primeUntil=now+1500
            postMessage("已选中  再次点按打开玻璃键盘")
            feedback.tap()
        }
    }

    val createPpt = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.presentationml.presentation")
    ) { uri ->
        val bytes=pendingPpt
        if(uri!=null && bytes!=null){
            scope.launch(Dispatchers.IO){
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: error("无法写入文件")
                }.onSuccess {
                    withContext(Dispatchers.Main){postMessage("PPTX 已保存")}
                }.onFailure {
                    withContext(Dispatchers.Main){postMessage("PPTX 保存失败")}
                }
            }
        }
        pendingPpt=null
    }

    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null){
            scope.launch {
                runCatching {
                    val bytes=withContext(Dispatchers.IO){context.contentResolver.openInputStream(uri)?.use{it.readBytes()} ?: error("文件为空")}
                    val name=queryName(context,uri).lowercase(Locale.getDefault())
                    if(name.endsWith(".pptx") || (bytes.size>2 && bytes[0].toInt()==0x50 && bytes[1].toInt()==0x4B)){
                        val fields=withContext(Dispatchers.Default){PptxCodec.importFields(bytes)}
                        controller.applyImportedFields(fields)
                        "PPTX 数字已导入"
                    }else{
                        controller.importJson(bytes.toString(Charsets.UTF_8))
                        "JSON 已导入"
                    }
                }.onSuccess{postMessage(it)}
                 .onFailure{postMessage("导入失败  请确认文件来自本工具")}
            }
        }
    }

    val pickMusic = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null){
            persistReadPermission(context,uri)
            controller.setCustomMusic(uri)
            runCatching { music.prepareCustom(uri,true) }
                .onFailure { postMessage("媒体格式不支持") }
        }
    }
    val pickBackground = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null){persistReadPermission(context,uri);controller.setCustomBackground(uri)}
    }
    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null){persistReadPermission(context,uri);controller.setCustomCover(uri)}
    }

    val colorScheme = if(controller.darkTheme) s44DarkColors() else s44LightColors()

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography
    ){
        Box(
            Modifier
                .fillMaxSize()
                .background(if(controller.darkTheme) Color(0xFF15131A) else Color(0xFFF9EAF3))
        ){
            if(bgBitmap!=null){
                Image(
                    bitmap=bgBitmap,
                    contentDescription=null,
                    modifier=Modifier.fillMaxSize(),
                    contentScale=ContentScale.Crop,
                    alpha=(1f-controller.backgroundVeil).coerceIn(.18f,1f)
                )
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(
                        colors=if(controller.darkTheme)
                            listOf(Color(0xC9131118),Color(0xBD191622),Color(0xC4121720))
                        else listOf(Color(0xB8FFDCEB),Color(0xA9EEE7FF),Color(0xA8E8F6FF)),
                        start=Offset.Zero,end=Offset(1200f,1600f)
                    )
                )
            )

            Scaffold(
                containerColor=Color.Transparent,
                topBar={
                    AppHeader(
                        controller=controller,
                        onTheme={
                            controller.setDark(!controller.darkTheme)
                            feedback.tap()
                        },
                        onHeart={feedback.tap();postMessage("S44 英雄战力工具  原生版")}
                    )
                },
                bottomBar={
                    if(padTarget==null){
                        NativeBottomBar(
                            selected=tab,
                            onSelected={
                                feedback.tap()
                                tab=it
                                primedId=null
                            }
                        )
                    }
                }
            ){ padding ->
                val scrollEnabled=padTarget==null
                when(tab){
                    AppTab.EXACT -> ExactScreen(
                        controller,padding,scrollEnabled,primedId,
                        onInputTap={id,label,value->tapInput(PadTarget(id,label),value)},
                        onFeedback={feedback.tap(true)}
                    )
                    AppTab.PEAK -> PeakScreen(
                        controller,padding,scrollEnabled,primedId,
                        onInputTap={id,label,value,data->tapInput(PadTarget(id,label,dataMode=data),value)},
                        onFeedback={feedback.tap(true)}
                    )
                    AppTab.MATCHES -> MatchesScreen(
                        controller,padding,scrollEnabled,primedId,
                        onInputTap={id,label,value,data->tapInput(PadTarget(id,label,dataMode=data),value)},
                        onFeedback={feedback.tap(true)}
                    )
                    AppTab.FILES -> FilesScreen(
                        controller,padding,scrollEnabled,primedId,
                        onInputTap={id,label,value->tapInput(PadTarget(id,label,qqMode=true),value)},
                        onImport={feedback.tap();importFile.launch(arrayOf("application/json","application/vnd.openxmlformats-officedocument.presentationml.presentation"))},
                        onExport={
                            feedback.tap(true)
                            scope.launch {
                                runCatching { withContext(Dispatchers.Default){PptxCodec.export(context,controller)} }
                                    .onSuccess { bytes ->
                                        pendingPpt=bytes
                                        val suffix=if(controller.isPptVerified()) "无水印" else "全屏灰色水印"
                                        createPpt.launch("秋天_S44战力估值_${suffix}_${timeStamp()}.pptx")
                                    }
                                    .onFailure { postMessage("PPTX 生成失败") }
                            }
                        },
                        onRestore={
                            controller.restorePptVerification()
                            feedback.tap(true)
                            postMessage("已恢复默认  下次需要重新验证")
                        }
                    )
                    AppTab.RULES -> RulesScreen(
                        controller,padding,scrollEnabled,
                        onLink={url->feedback.tap();openUrl(context,url)}
                    )
                    AppTab.STYLE -> StyleScreen(
                        controller,padding,scrollEnabled,
                        onPickBackground={pickBackground.launch(arrayOf("image/*"))},
                        onPickCover={pickCover.launch(arrayOf("image/*"))},
                        onPickMusic={pickMusic.launch(arrayOf("audio/*","video/mp4","application/octet-stream"))},
                        onFeedback={feedback.tap()}
                    )
                }
            }

            if(!showNotice && padTarget==null){
                MusicDock(
                    controller=controller,
                    music=music,
                    expanded=showMusicPanel,
                    onTogglePanel={showMusicPanel=!showMusicPanel;feedback.tap()},
                    onPrevious={feedback.tap();music.previous()},
                    onPlayPause={feedback.tap(true);music.toggle()},
                    onNext={feedback.tap();music.next()},
                    onLoop={
                        controller.setMusicLoop(!controller.musicLoop)
                        music.setLoop(controller.musicLoop)
                        feedback.tap()
                    }
                )
            }

            AnimatedVisibility(
                visible=padTarget!=null,
                modifier=Modifier.align(Alignment.BottomCenter),
                enter=slideInVertically(animationSpec=tween(190)){it}+fadeIn(tween(120)),
                exit=slideOutVertically(animationSpec=tween(170)){it}+fadeOut(tween(90))
            ){
                padTarget?.let { target ->
                    val value=valueFor(controller,target.id)
                    GlassDataKeypad(
                        target=target,
                        value=value,
                        caret=padCaret,
                        onCaret={padCaret=it.coerceIn(0,value.length)},
                        onValue={next,nextCaret->
                            controller.update(target.id,next)
                            padCaret=nextCaret.coerceIn(0,next.length)
                            if(target.id=="pptAuthor" &&
                                controller.pptAuthorInput==AppController.PPT_AUTHOR_QQ &&
                                !controller.pptRemembered){
                                showPptMemoryChoice=true
                            }
                        },
                        onDone={feedback.tap(true);padTarget=null},
                        onNext={
                            val next=nextPadTarget(target.id)
                            if(next!=null){
                                padTarget=next
                                padCaret=valueFor(controller,next.id).length
                            }else padTarget=null
                        },
                        feedback=feedback
                    )
                }
            }

            if(showNotice){
                NoticeDialog(
                    controller=controller,
                    onConfirm={
                        feedback.tap(true)
                        showNotice=false
                        if(controller.musicAutoplay) music.prepareDefault(true)
                    },
                    onAutoplay={controller.setMusicAutoplay(it)},
                    onLoop={controller.setMusicLoop(it)}
                )
            }

            if(showPptMemoryChoice){
                AlertDialog(
                    onDismissRequest={},
                    containerColor=if(controller.darkTheme) Color(0xF52A2430) else Color(0xF9FFF8FC),
                    title={Text("验证成功",fontWeight=FontWeight.Bold)},
                    text={Text("下次是否继续输入作者 QQ")},
                    confirmButton={
                        TextButton(onClick={
                            controller.rememberPptVerification(true)
                            showPptMemoryChoice=false
                            feedback.tap(true)
                        }){Text("下次不输入")}
                    },
                    dismissButton={
                        TextButton(onClick={
                            controller.rememberPptVerification(false)
                            showPptMemoryChoice=false
                            feedback.tap()
                        }){Text("下次输入")}
                    }
                )
            }

            message?.let { msg ->
                LaunchedEffect(msg){
                    kotlinx.coroutines.delay(1800)
                    if(message==msg)message=null
                }
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom=if(padTarget==null) 92.dp else 330.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xCC443B49))
                        .padding(horizontal=16.dp,vertical=10.dp)
                ){
                    Text(msg,color=Color.White,fontSize=12.sp,fontWeight=FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun AppHeader(
    controller:AppController,
    onTheme:()->Unit,
    onHeart:()->Unit
){
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal=18.dp,vertical=12.dp),
        verticalAlignment=Alignment.CenterVertically
    ){
        Image(
            painter=painterResource(R.drawable.author_avatar),
            contentDescription="秋天",
            modifier=Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)),
            contentScale=ContentScale.Crop
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)){
            Text(
                "S44 英雄战力工具",
                fontSize=23.sp,
                lineHeight=26.sp,
                fontWeight=FontWeight.Bold,
                color=MaterialTheme.colorScheme.onBackground,
                maxLines=1,
                overflow=TextOverflow.Ellipsis
            )
            Row(verticalAlignment=Alignment.CenterVertically){
                Text("公式精确优先 · 作者 ",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text("秋天",fontSize=12.sp,color=AuthorGold,fontWeight=FontWeight.Bold)
            }
        }
        GlassSquareButton("♡",onHeart)
        Spacer(Modifier.width(8.dp))
        GlassSquareButton(if(controller.darkTheme)"☾" else "☀",onTheme)
    }
}

@Composable
private fun GlassSquareButton(text:String,onClick:()->Unit){
    Box(
        Modifier
            .size(52.dp)
            .shadow(7.dp,RoundedCornerShape(18.dp),ambientColor=Color(0x1E5D4868))
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha=.48f),Color(0xFFEDE6FF).copy(alpha=.30f))
                )
            )
            .border(1.dp,Color.White.copy(alpha=.55f),RoundedCornerShape(18.dp))
            .clickable(onClick=onClick),
        contentAlignment=Alignment.Center
    ){
        Text(text,fontSize=28.sp,fontWeight=FontWeight.Medium,color=MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun GlassCard(
    controller:AppController,
    modifier:Modifier=Modifier,
    contentPadding:PaddingValues=PaddingValues(16.dp),
    content:@Composable ColumnScope.()->Unit
){
    val alpha=controller.glassAlpha
    val shape=RoundedCornerShape(28.dp)
    Column(
        modifier
            .fillMaxWidth()
            .shadow((8f + controller.glassBlur * .32f).dp,shape,ambientColor=Color(0x1B654C74))
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha=(alpha+.10f).coerceAtMost(.92f)),
                        Color(0xFFF6EAF6).copy(alpha=(alpha-.10f).coerceAtLeast(.18f)),
                        Color(0xFFE9EDFF).copy(alpha=(alpha-.14f).coerceAtLeast(.16f))
                    )
                )
            )
            .border(1.dp,Color.White.copy(alpha=.60f),shape)
            .padding(contentPadding),
        verticalArrangement=Arrangement.spacedBy(12.dp),
        content=content
    )
}

@Composable
private fun SectionTitle(title:String,badge:String?=null,badgeColor:Color=GoodGreen){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
        Text(title,fontSize=21.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onBackground,modifier=Modifier.weight(1f))
        badge?.let{
            Box(
                Modifier.clip(RoundedCornerShape(999.dp))
                    .background(badgeColor.copy(alpha=.12f))
                    .border(1.dp,badgeColor.copy(alpha=.22f),RoundedCornerShape(999.dp))
                    .padding(horizontal=11.dp,vertical=6.dp)
            ){Text(it,color=badgeColor,fontSize=10.sp,fontWeight=FontWeight.Bold)}
        }
    }
}

@Composable
private fun HintBox(text:String,strong:Boolean=false){
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha=.26f))
            .border(1.dp,Color.White.copy(alpha=.35f),RoundedCornerShape(20.dp))
            .padding(14.dp)
    ){
        Text(
            text,
            fontSize=13.sp,
            lineHeight=22.sp,
            fontWeight=if(strong) FontWeight.SemiBold else FontWeight.Medium,
            color=MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GlassInput(
    label:String,
    hint:String?,
    value:String,
    color:Color=MaterialTheme.colorScheme.onBackground,
    primed:Boolean=false,
    multiline:Boolean=false,
    onClick:()->Unit
){
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
        Row(Modifier.fillMaxWidth()){
            Text(label,fontSize=12.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.weight(1f))
            hint?.let{Text(it,fontSize=10.sp,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        }
        Box(
            Modifier
                .fillMaxWidth()
                .requiredHeightIn(min=if(multiline) 160.dp else 66.dp,max=if(multiline) 240.dp else 84.dp)
                .clip(RoundedCornerShape(19.dp))
                .background(Color.White.copy(alpha=.25f))
                .border(
                    if(primed) 1.8.dp else 1.1.dp,
                    if(primed) PerfPurple.copy(alpha=.58f) else MaterialTheme.colorScheme.outline.copy(alpha=.22f),
                    RoundedCornerShape(19.dp)
                )
                .clickable(onClick=onClick)
                .padding(horizontal=15.dp,vertical=14.dp)
        ){
            Text(
                if(value.isBlank()) "点按输入" else value,
                fontSize=if(multiline) 15.sp else 23.sp,
                lineHeight=if(multiline) 24.sp else 27.sp,
                fontWeight=if(multiline) FontWeight.Medium else FontWeight.Bold,
                color=if(value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.46f) else color,
                maxLines=if(multiline) 8 else 1,
                overflow=TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ExactScreen(
    c:AppController,
    padding:PaddingValues,
    scrollEnabled:Boolean,
    primedId:String?,
    onInputTap:(String,String,String)->Unit,
    onFeedback:()->Unit
){
    val r=c.exactResult
    val scroll=rememberScrollState()
    Column(
        Modifier.fillMaxSize().padding(padding).padding(horizontal=18.dp)
            .verticalScroll(scroll,enabled=scrollEnabled)
            .padding(bottom=116.dp),
        verticalArrangement=Arrangement.spacedBy(14.dp)
    ){
        GlassCard(c){
            SectionTitle("游戏内实值输入","公式精确模式",GoodGreen)
            HintBox("这页不猜巅峰系数  把王者荣耀战力详情中实际显示的数据填进来  工具只做官方公式的数学运算",true)

            GlassInput("目标英雄战力","例如 10000",c.targetPower,PowerRed,primedId=="targetPower"){
                onInputTap("targetPower","目标英雄战力",c.targetPower)
            }
            GlassInput("当前游戏英雄战力","可选  用于自校验",c.gamePower,PowerRed,primedId=="gamePower"){
                onInputTap("gamePower","当前游戏英雄战力",c.gamePower)
            }

            RankSelector(c)

            GlassInput("当前表现分","",c.performance,PerfPurple,primedId=="performance"){
                onInputTap("performance","当前表现分",c.performance)
            }
            GlassInput("表现分上限","游戏内实值",c.performanceCap,PerfPurple,primedId=="performanceCap"){
                onInputTap("performanceCap","表现分上限",c.performanceCap)
            }
            GlassInput("当前巅峰分","",c.peakNow,PeakBlue,primedId=="peakNow"){
                onInputTap("peakNow","当前巅峰分",c.peakNow)
            }
            GlassInput("英雄已解锁巅峰分","",c.peakUnlocked,PeakBlue,primedId=="peakUnlocked"){
                onInputTap("peakUnlocked","英雄已解锁巅峰分",c.peakUnlocked)
            }
            GlassInput("游戏显示巅峰系数","152.6 或 1.526",c.coefficientInput,CoeffGold,primedId=="coefficientInput"){
                onInputTap("coefficientInput","游戏显示巅峰系数",c.coefficientInput)
            }

            PrimaryButton("重新计算",onClick=onFeedback)
        }

        GlassCard(c){
            SectionTitle("公式结果",if(r.valid)"已计算" else "等待系数",if(r.valid) GoodGreen else WarnOrange)
            MetricBig("英雄战力",if(r.valid) S44Math.fmtInt(r.power) else "—",PowerRed)
            MetricRow(
                listOf(
                    "计入表现分" to if(r.valid) S44Math.fmtInt(r.effectivePerf) else "—",
                    "总巅峰系数" to if(r.valid) "%.2f%%".format(Locale.US,r.coeff*100.0) else "—"
                ),
                listOf(PerfPurple,CoeffGold)
            )
            MetricRow(
                listOf(
                    "目标所需表现分" to if(r.valid) S44Math.fmtInt(r.needPerf) else "—",
                    "打满后战力" to if(r.valid) S44Math.fmtInt(r.capPower) else "—"
                ),
                listOf(PerfPurple,PowerRed)
            )
            HintBox("${r.selfCheck}  ${r.selfCheckSub}")
            if(r.decision.isNotBlank())HintBox(r.decision)
        }
    }
}

@Composable
private fun RankSelector(c:AppController){
    Column(verticalArrangement=Arrangement.spacedBy(7.dp)){
        Row(Modifier.fillMaxWidth()){
            Text("当前排位段位",fontSize=12.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.weight(1f))
            Text("段位快捷预设",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val options=listOf("diamond" to "钻石","stellar" to "星耀","king" to "最强王者及以上","manual" to "手动")
        Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
            options.chunked(2).forEach { row ->
                Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){
                    row.forEach{(id,label)->
                        val selected=c.rankTier==id
                        Box(
                            Modifier.weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if(selected) PerfPurple.copy(alpha=.13f) else Color.White.copy(alpha=.20f))
                                .border(1.dp,if(selected) PerfPurple.copy(alpha=.28f) else Color.White.copy(alpha=.24f),RoundedCornerShape(14.dp))
                                .clickable{c.setRankTier(id)}
                                .padding(11.dp),
                            contentAlignment=Alignment.Center
                        ){Text(label,fontSize=11.sp,fontWeight=FontWeight.Bold,color=if(selected) PerfPurple else MaterialTheme.colorScheme.onSurfaceVariant)}
                    }
                }
            }
        }
    }
}

@Composable
private fun PeakScreen(
    c:AppController,
    padding:PaddingValues,
    scrollEnabled:Boolean,
    primedId:String?,
    onInputTap:(String,String,String,Boolean)->Unit,
    onFeedback:()->Unit
){
    val r=c.peakResult
    val scroll=rememberScrollState()
    Column(
        Modifier.fillMaxSize().padding(padding).padding(horizontal=18.dp)
            .verticalScroll(scroll,enabled=scrollEnabled).padding(bottom=116.dp),
        verticalArrangement=Arrangement.spacedBy(14.dp)
    ){
        GlassCard(c){
            SectionTitle("巅峰分 → 巅峰系数映射表",if(S44Math.parseCurve(c.curveText).size>=2)"已导入映射" else "未导入完整官方表",WarnOrange)
            HintBox("公开公告没有给出每一个积分点的完整数值表  精准反推需要游戏内实测映射数据  没有完整表时只能估算")
            GlassKeyboardTip()
            GlassInput("粘贴/录入映射表","格式  巅峰分,总倍率%",c.curveText,PeakBlue,primedId=="curveText",multiline=true){
                onInputTap("curveText","巅峰分与系数映射表",c.curveText,true)
            }
            Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){
                SecondaryButton("清空",Modifier.weight(1f)){c.update("curveText","")}
                PrimaryButton("应用映射表",Modifier.weight(1f)){onFeedback()}
            }
            SettingSwitch("允许参考曲线估算",c.estimateAllowed){c.setEstimate(it)}
        }

        GlassCard(c){
            SectionTitle("目标巅峰反推",if(r.needPeakScore!=null) r.precision else "等待映射表",if(r.source=="reference") WarnOrange else GoodGreen)
            MetricBig("目标所需巅峰系数","%.2f%%".format(Locale.US,r.needCoeffPct),CoeffGold)
            MetricRow(
                listOf(
                    "目标巅峰分" to (r.needPeakScore?.toString()?:"—"),
                    "与当前差值" to (r.gap?.let{if(it>=0)"+$it" else "$it"}?:"—")
                ),
                listOf(PeakBlue,PeakBlue)
            )
            HintBox(
                when(r.source){
                    "import"->"使用你录入的实测映射表进行反推"
                    "reference"->"当前为参考曲线估算  不冒充官方完整表"
                    else->"请先录入完整映射表  或手动开启参考曲线估算"
                }
            )
        }
    }
}

@Composable
private fun MatchesScreen(
    c:AppController,
    padding:PaddingValues,
    scrollEnabled:Boolean,
    primedId:String?,
    onInputTap:(String,String,String,Boolean)->Unit,
    onFeedback:()->Unit
){
    val r=c.matchResult
    val scroll=rememberScrollState()
    Column(
        Modifier.fillMaxSize().padding(padding).padding(horizontal=18.dp)
            .verticalScroll(scroll,enabled=scrollEnabled).padding(bottom=116.dp),
        verticalArrangement=Arrangement.spacedBy(14.dp)
    ){
        GlassCard(c){
            SectionTitle("45 场表现分计算","单局表现分可精确",GoodGreen)
            HintBox("S40 规则  直接输入游戏结算后的单局表现分  工具取最高 45 场并应用表现分上限  评分×倍率仅作辅助估算")
            GlassKeyboardTip()
            GlassInput(
                "对局数据","一行一场",c.matchText,MatchCyan,primedId=="matchText",multiline=true
            ){onInputTap("matchText","45 场对局数据",c.matchText,true)}
            HintBox("推荐格式\n156.4\n142.8\n...\n也支持辅助估算  评分,倍率")

            GlassInput("表现分上限","同步精确页",c.matchCap,PerfPurple,primedId=="matchCap"){
                onInputTap("matchCap","表现分上限",c.matchCap,false)
            }
            GlassInput("周回收比例","S40 公告 15%",c.recycleRate,MaterialTheme.colorScheme.onBackground,primedId=="recycleRate"){
                onInputTap("recycleRate","周回收比例",c.recycleRate,false)
            }
            Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){
                SecondaryButton("清空",Modifier.weight(1f)){c.update("matchText","")}
                PrimaryButton("计算 45 场",Modifier.weight(1f)){onFeedback()}
            }
        }

        GlassCard(c){
            SectionTitle("对局结果",if(r.values.isEmpty())"等待数据" else "已按明细计算",if(r.values.isEmpty()) WarnOrange else GoodGreen)
            MetricBig("表现分",S44Math.fmtInt(r.performance),PerfPurple)
            MetricRow(
                listOf(
                    "输入场数" to r.values.size.toString(),
                    "进入最高场数" to r.best.size.toString()
                ),
                listOf(MatchCyan,MatchCyan)
            )
            MetricRow(
                listOf(
                    "原始累计" to S44Math.fmt1(r.raw),
                    "周回收场数" to r.recycleGames.toString()
                ),
                listOf(PerfPurple,MatchCyan)
            )
            if(r.values.isNotEmpty()){
                HintBox("已从 ${r.values.size} 场中取最高 ${r.best.size} 场  应用上限后为 ${S44Math.fmt1(r.performance)}")
                r.values.take(20).forEachIndexed{i,v->
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                        Text("${i+1}",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.width(36.dp))
                        Text(S44Math.fmt1(v),fontSize=12.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                        Text(if(i<45)"进入" else "未进入",fontSize=10.sp,color=if(i<45) GoodGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if(i<19)HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.08f))
                }
            }
        }
    }
}

@Composable
private fun FilesScreen(
    c:AppController,
    padding:PaddingValues,
    scrollEnabled:Boolean,
    primedId:String?,
    onInputTap:(String,String,String)->Unit,
    onImport:()->Unit,
    onExport:()->Unit,
    onRestore:()->Unit
){
    val scroll=rememberScrollState()
    Column(
        Modifier.fillMaxSize().padding(padding).padding(horizontal=18.dp)
            .verticalScroll(scroll,enabled=scrollEnabled).padding(bottom=116.dp),
        verticalArrangement=Arrangement.spacedBy(14.dp)
    ){
        GlassCard(c){
            SectionTitle("导入计算表","原生文件")
            HintBox("支持 JSON 和本工具导出的 PPTX  导入后会恢复输入参数并重新计算")
            PrimaryButton("选择文件并导入",onClick=onImport)
        }

        GlassCard(c){
            SectionTitle("PPTX 计算报告","原生 OOXML",WarnOrange)
            HintBox("由 Kotlin 直接生成 .pptx  战力红  系数黄  巅峰分蓝  表现分与星数紫  每页固定作者头像")
            val state=when{
                c.pptRemembered->"已记忆  无需再次输入"
                c.isPptVerified()->"验证成功  无水印"
                else->"未验证  全屏灰色秋天水印"
            }
            Text("作者验证   $state",fontSize=12.sp,fontWeight=FontWeight.Bold,color=if(c.isPptVerified()) GoodGreen else AuthorGold)
            GlassInput("作者 QQ","再次点按打开玻璃数字键盘",c.pptAuthorInput,AuthorGold,primedId=="pptAuthor"){
                onInputTap("pptAuthor","作者 QQ 验证",c.pptAuthorInput)
            }
            HintBox("未验证也可导出  每页全屏灰色秋天水印  去水印请联系作者")
            Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){
                SecondaryButton("恢复默认",Modifier.weight(1f),onRestore)
                PrimaryButton(if(c.isPptVerified())"导出无水印 PPTX" else "导出水印 PPTX",Modifier.weight(1.4f),onExport)
            }
        }
    }
}

@Composable
private fun RulesScreen(
    c:AppController,
    padding:PaddingValues,
    scrollEnabled:Boolean,
    onLink:(String)->Unit
){
    LaunchedEffect(Unit){checkOfficialStatus(c)}
    val scroll=rememberScrollState()
    Column(
        Modifier.fillMaxSize().padding(padding).padding(horizontal=18.dp)
            .verticalScroll(scroll,enabled=scrollEnabled).padding(bottom=116.dp),
        verticalArrangement=Arrangement.spacedBy(14.dp)
    ){
        GlassCard(c){
            SectionTitle("规则审计 · 只放能追溯到的内容","S44 核对版",GoodGreen)
            HintBox("官方明确  能在王者荣耀官网公告找到\n数学精确  在公开公式上用游戏实值直接计算\n估算  依赖未公开映射或未来对局")
            RuleRow("1","S40 英雄战力系统重构","表现分 × 巅峰分路系数  表现分按赛季高分记录累计")
            RuleRow("2","S40 周回收机制","每周回收最低 15% 对局")
            RuleRow("3","表现分上限","达到上限后战力按上限计入  额外分数作为滚动记录")
            RuleRow("4","S41 净胜场解锁巅峰分","英雄巅峰系数按每 100 分阶段净胜场解锁")
        }

        GlassCard(c){
            Row(verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){
                    Text("王者荣耀官网动态",fontSize=17.sp,fontWeight=FontWeight.Bold)
                    Text("腾讯官网直达",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val color=when(c.officialStatusOk){true->GoodGreen;false->WarnOrange;null->AuthorGold}
                Badge(c.officialStatus,color)
            }
            OfficialSeasonCard(onLink)
            OfficialGrid(onLink)
            HintBox("所有入口均为 pvp.qq.com  原生 App 跳转浏览器  返回后不会重置当前页面")
        }
    }
}

@Composable
private fun RuleRow(index:String,title:String,body:String){
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.Top){
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFFFFEAF4)),
            contentAlignment=Alignment.Center
        ){Text(index,fontWeight=FontWeight.Bold,color=Color(0xFF745D78))}
        Column(Modifier.weight(1f)){
            Text(title,fontSize=13.sp,fontWeight=FontWeight.Bold)
            Text(body,fontSize=11.sp,lineHeight=18.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("官网",fontSize=10.sp,color=Color(0xFFE86DA9),fontWeight=FontWeight.Bold)
    }
}

@Composable
private fun OfficialSeasonCard(onLink:(String)->Unit){
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(Color(0x55FFE4F0),Color(0x55E5E1FF))))
            .border(1.dp,Color.White.copy(.36f),RoundedCornerShape(18.dp))
            .clickable{onLink("https://pvp.qq.com/web201706/newsdetail.shtml?tid=802692")}
            .padding(11.dp),
        verticalAlignment=Alignment.CenterVertically
    ){
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Color(0xFFE66BA6),Color(0xFF8970E7)))),contentAlignment=Alignment.Center){
            Text("S44",color=Color.White,fontWeight=FontWeight.Bold,fontSize=12.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)){
            Text("不拘命格",fontSize=12.sp,fontWeight=FontWeight.Bold)
            Text("赛季官网公告",fontSize=9.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("↗",fontSize=20.sp,color=PerfPurple)
    }
}

@Composable
private fun OfficialGrid(onLink:(String)->Unit){
    val links=listOf(
        Triple("更新爆料","官方新闻与版本公告","https://pvp.qq.com/web201706/newsindex.shtml"),
        Triple("英雄爆料","体验服新英雄与调整","https://pvp.qq.com/cp/a20161115tyf/index.shtml"),
        Triple("BUG 爆料","体验服问题与反馈","https://pvp.qq.com/cp/a20161115tyf/index.shtml"),
        Triple("实时公告","正式服最新动态","https://pvp.qq.com/web201706/newsindex.shtml"),
        Triple("王者官网","腾讯王者荣耀官网","https://pvp.qq.com/"),
        Triple("体验服","前瞻版本官方专区","https://pvp.qq.com/cp/a20161115tyf/index.shtml")
    )
    links.chunked(2).forEach{row->
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            row.forEach{(title,sub,url)->
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha=.23f))
                        .border(1.dp,Color.White.copy(alpha=.30f),RoundedCornerShape(16.dp))
                        .clickable{onLink(url)}
                        .padding(11.dp)
                ){
                    Column{
                        Text(title,fontSize=11.sp,fontWeight=FontWeight.Bold)
                        Text(sub,fontSize=8.5.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("↗",modifier=Modifier.align(Alignment.CenterEnd),color=PerfPurple)
                }
            }
        }
    }
}

@Composable
private fun StyleScreen(
    c:AppController,
    padding:PaddingValues,
    scrollEnabled:Boolean,
    onPickBackground:()->Unit,
    onPickCover:()->Unit,
    onPickMusic:()->Unit,
    onFeedback:()->Unit
){
    val scroll=rememberScrollState()
    Column(
        Modifier.fillMaxSize().padding(padding).padding(horizontal=18.dp)
            .verticalScroll(scroll,enabled=scrollEnabled).padding(bottom=116.dp),
        verticalArrangement=Arrangement.spacedBy(14.dp)
    ){
        GlassCard(c){
            SectionTitle("外观","LiquidGlass")
            SettingSwitch("深色模式",c.darkTheme){c.setDark(it);onFeedback()}
            SettingSwitch("按钮震动",c.haptic){c.setHaptic(it)}
            SettingSwitch("按钮点击声音",c.uiSound){c.setUiSound(it);onFeedback()}

            SliderSetting("字体粗细",c.fontWeight,400f..700f){c.setFontWeight(it)}
            SliderSetting("玻璃透明度",c.glassAlpha,.25f..0.90f){c.setGlassAlpha(it)}
            SliderSetting("玻璃模糊强度",c.glassBlur,0f..32f){c.setGlassBlur(it)}
            SliderSetting("背景遮罩",c.backgroundVeil,0f..0.65f){c.setBackgroundVeil(it)}
        }

        GlassCard(c){
            SectionTitle("背景与封面","本地资源")
            PrimaryButton("选择自定义背景",onClick=onPickBackground)
            SecondaryButton("选择公告封面",onClick=onPickCover)
        }

        GlassCard(c){
            SectionTitle("音乐","3 首内置")
            Text("① 魔女大冒险  →  ② 那天下雨了  →  ③ 伊波恩古董店",fontSize=11.sp,lineHeight=18.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            SettingSwitch("下次启动自动播放",c.musicAutoplay){c.setMusicAutoplay(it)}
            SettingSwitch("一直循环播放",c.musicLoop){c.setMusicLoop(it)}
            SliderSetting("音量",c.musicVolume,0f..1f){c.setMusicVolume(it)}
            SecondaryButton("导入自定义音乐",onClick=onPickMusic)
        }
    }
}

@Composable
private fun SliderSetting(label:String,value:Float,range:ClosedFloatingPointRange<Float>,onChange:(Float)->Unit){
    Column{
        Row(Modifier.fillMaxWidth()){
            Text(label,fontSize=12.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
            Text("%.0f".format(Locale.US,if(range.endInclusive<=1f)value*100 else value),fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value=value.coerceIn(range.start,range.endInclusive),onValueChange=onChange,valueRange=range)
    }
}

@Composable
private fun SettingSwitch(label:String,checked:Boolean,onChange:(Boolean)->Unit){
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha=.18f)).padding(horizontal=12.dp,vertical=8.dp),
        verticalAlignment=Alignment.CenterVertically
    ){
        Text(label,fontSize=12.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
        Switch(
            checked=checked,onCheckedChange=onChange,
            colors=SwitchDefaults.colors(checkedThumbColor=Color.White,checkedTrackColor=PerfPurple)
        )
    }
}

@Composable
private fun MetricBig(label:String,value:String,color:Color){
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha=.22f)).padding(14.dp)
    ){
        Text(label,fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,fontWeight=FontWeight.Bold)
        Text(value,fontSize=38.sp,lineHeight=42.sp,color=color,fontWeight=FontWeight.Bold)
    }
}

@Composable
private fun MetricRow(items:List<Pair<String,String>>,colors:List<Color>){
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        items.forEachIndexed{i,(label,value)->
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha=.18f)).padding(12.dp)
            ){
                Text(label,fontSize=9.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,fontWeight=FontWeight.Bold)
                Text(value,fontSize=19.sp,color=colors.getOrElse(i){MaterialTheme.colorScheme.onBackground},fontWeight=FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PrimaryButton(text:String,modifier:Modifier=Modifier,onClick:()->Unit){
    Button(
        onClick=onClick,
        modifier=modifier.fillMaxWidth().height(52.dp),
        shape=RoundedCornerShape(18.dp),
        colors=ButtonDefaults.buttonColors(containerColor=Color.Transparent),
        contentPadding=PaddingValues(0.dp)
    ){
        Box(
            Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFFF06AA8),Color(0xFF8A6AE8)))),
            contentAlignment=Alignment.Center
        ){Text(text,color=Color.White,fontWeight=FontWeight.Bold)}
    }
}

@Composable
private fun SecondaryButton(text:String,modifier:Modifier=Modifier,onClick:()->Unit){
    Button(
        onClick=onClick,
        modifier=modifier.fillMaxWidth().height(48.dp),
        shape=RoundedCornerShape(17.dp),
        colors=ButtonDefaults.buttonColors(containerColor=Color.White.copy(alpha=.28f)),
        border=BorderStroke(1.dp,Color.White.copy(alpha=.42f))
    ){Text(text,color=MaterialTheme.colorScheme.onBackground,fontWeight=FontWeight.Bold)}
}

@Composable
private fun Badge(text:String,color:Color){
    Box(Modifier.clip(RoundedCornerShape(999.dp)).background(color.copy(alpha=.12f)).border(1.dp,color.copy(alpha=.22f),RoundedCornerShape(999.dp)).padding(horizontal=10.dp,vertical=6.dp)){
        Text(text,color=color,fontSize=9.sp,fontWeight=FontWeight.Bold)
    }
}

@Composable
private fun GlassKeyboardTip(){
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(Color(0x35E8D9FF),Color(0x35D9F0FF))))
            .border(1.dp,PerfPurple.copy(alpha=.18f),RoundedCornerShape(14.dp))
            .padding(10.dp),
        horizontalArrangement=Arrangement.spacedBy(7.dp)
    ){
        Text("玻璃数据键盘",fontSize=10.sp,fontWeight=FontWeight.Bold,color=PerfPurple)
        Text("已关闭系统输入法  二次点按输入框即可输入  支持小数  逗号  换行  加减和光标移动",fontSize=9.5.sp,lineHeight=15.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.weight(1f))
    }
}

@Composable
private fun NativeBottomBar(selected:AppTab,onSelected:(AppTab)->Unit){
    val tabs=AppTab.entries
    BoxWithConstraints(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=6.dp,vertical=6.dp)
            .height(78.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(Color.White.copy(alpha=.78f))
            .border(1.dp,Color.White.copy(alpha=.72f),RoundedCornerShape(32.dp))
    ){
        val itemWidth=maxWidth/tabs.size
        val x by animateDpAsState(itemWidth*selected.ordinal,animationSpec=tween(260),label="nav")
        Box(
            Modifier.offset(x=x).width(itemWidth).fillMaxHeight().padding(5.dp)
                .clip(RoundedCornerShape(27.dp))
                .background(Brush.linearGradient(listOf(Color(0x66FFD6E9),Color(0x66DCD8FF))))
                .border(1.dp,PerfPurple.copy(alpha=.12f),RoundedCornerShape(27.dp))
        ){
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom=5.dp).width(21.dp).height(3.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(Color(0xFFE96AA9),Color(0xFF8A6CE5)))))
        }
        Row(Modifier.fillMaxSize()){
            tabs.forEach{tab->
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable{onSelected(tab)},
                    horizontalAlignment=Alignment.CenterHorizontally,
                    verticalArrangement=Arrangement.Center
                ){
                    Text(tab.icon,fontSize=if(tab==AppTab.MATCHES) 16.sp else 24.sp,fontWeight=FontWeight.Bold,color=if(selected==tab) Color(0xFF4A3C53) else Color(0xFF796E80))
                    Text(tab.title,fontSize=9.sp,fontWeight=FontWeight.Bold,color=if(selected==tab) Color(0xFF4A3C53) else Color(0xFF796E80))
                }
            }
        }
    }
}

@Composable
private fun GlassDataKeypad(
    target:PadTarget,
    value:String,
    caret:Int,
    onCaret:(Int)->Unit,
    onValue:(String,Int)->Unit,
    onDone:()->Unit,
    onNext:()->Unit,
    feedback:UiFeedback
){
    val keys=listOf(
        listOf("7","8","9","⌫"),
        listOf("4","5","6","清空"),
        listOf("1","2","3","."),
        listOf("0","00","下一项","确定")
    )

    fun insert(token:String){
        var t=token
        if(target.qqMode){
            if(!token.all{it.isDigit()})return
            if(value.length+token.length>10)return
        }
        if(!target.dataMode && !target.qqMode && token=="."){
            if(value.contains("."))return
            if(value.isBlank()) {
                onValue("0.",2);return
            }
        }
        if(target.dataMode && token=="."){
            val left=value.substring(0,caret.coerceIn(0,value.length))
            val current=left.split(Regex("[,\\n;，\\s]+")).lastOrNull()?:""
            if(current.contains("."))return
        }
        val at=caret.coerceIn(0,value.length)
        val next=value.substring(0,at)+t+value.substring(at)
        onValue(next,at+t.length)
        feedback.tap()
    }

    Column(
        Modifier.fillMaxWidth()
            .navigationBarsPadding()
            .shadow(20.dp,RoundedCornerShape(topStart=28.dp,topEnd=28.dp),ambientColor=Color(0x2A533D60))
            .clip(RoundedCornerShape(topStart=28.dp,topEnd=28.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xF3FFF8FC),Color(0xF0EFECFF),Color(0xEFF1F8FF))
                )
            )
            .border(1.dp,Color.White.copy(.85f),RoundedCornerShape(topStart=28.dp,topEnd=28.dp))
            .padding(horizontal=10.dp,vertical=10.dp),
        verticalArrangement=Arrangement.spacedBy(8.dp)
    ){
        Row(verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){
                Text(target.label,fontSize=10.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurfaceVariant)
                KeypadValueEditor(value,caret,onCaret)
            }
            SecondaryMini("全部删除"){
                feedback.tap()
                onValue("",0)
            }
            Spacer(Modifier.width(7.dp))
            SecondaryMini("完成"){feedback.tap(true);onDone()}
        }

        keys.forEach{row->
            Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){
                row.forEach{key->
                    PadButton(
                        key,
                        modifier=Modifier.weight(1f),
                        primary=key=="确定"
                    ){
                        when(key){
                            "⌫"->{
                                if(caret>0){
                                    val at=caret.coerceIn(0,value.length)
                                    onValue(value.removeRange(at-1,at),at-1)
                                }
                                feedback.tap()
                            }
                            "清空"->{feedback.tap();onValue("",0)}
                            "下一项"->{feedback.tap();onNext()}
                            "确定"->{feedback.tap(true);onDone()}
                            else->insert(key)
                        }
                    }
                }
            }
        }

        if(target.dataMode){
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                listOf("←","→",",","换行",";","+","−").forEach{key->
                    PadButton(key,Modifier.weight(1f),small=true){
                        when(key){
                            "←"->{onCaret((caret-1).coerceAtLeast(0));feedback.tap()}
                            "→"->{onCaret((caret+1).coerceAtMost(value.length));feedback.tap()}
                            "换行"->insert("\n")
                            "−"->insert("-")
                            else->insert(key)
                        }
                    }
                }
            }
        }else{
            Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){
                PadButton("←",Modifier.weight(1f),small=true){onCaret((caret-1).coerceAtLeast(0));feedback.tap()}
                PadButton("→",Modifier.weight(1f),small=true){onCaret((caret+1).coerceAtMost(value.length));feedback.tap()}
                PadButton("−",Modifier.weight(1f),small=true){insert("-")}
                PadButton("+",Modifier.weight(1f),small=true){insert("+")}
            }
        }
    }
}

@Composable
private fun KeypadValueEditor(value:String,caret:Int,onCaret:(Int)->Unit){
    val shown=if(value.isEmpty()) "│" else buildString{
        val c=caret.coerceIn(0,value.length)
        append(value.substring(0,c))
        append("│")
        append(value.substring(c))
    }.replace("\n"," ↵ ")
    Text(
        shown,
        modifier=Modifier.fillMaxWidth(.94f).padding(top=3.dp)
            .pointerInput(value){
                detectTapGestures{offset->
                    val ratio=(offset.x/size.width.toFloat()).coerceIn(0f,1f)
                    onCaret((ratio*value.length).roundToInt().coerceIn(0,value.length))
                }
            },
        fontSize=27.sp,
        lineHeight=31.sp,
        fontWeight=FontWeight.Bold,
        color=Color(0xFF554A5C),
        maxLines=2,
        overflow=TextOverflow.Ellipsis
    )
}

@Composable
private fun SecondaryMini(text:String,onClick:()->Unit){
    Box(
        Modifier.clip(RoundedCornerShape(15.dp)).background(Color.White.copy(alpha=.46f))
            .border(1.dp,Color.White.copy(alpha=.65f),RoundedCornerShape(15.dp))
            .clickable(onClick=onClick).padding(horizontal=12.dp,vertical=10.dp)
    ){Text(text,fontSize=10.sp,fontWeight=FontWeight.Bold)}
}

@Composable
private fun PadButton(text:String,modifier:Modifier=Modifier,primary:Boolean=false,small:Boolean=false,onClick:()->Unit){
    Box(
        modifier.height(if(small)38.dp else 54.dp)
            .clip(RoundedCornerShape(if(small)12.dp else 17.dp))
            .background(
                if(primary) Brush.linearGradient(listOf(Color(0xFFF4A7CC),Color(0xFFC1B8FF)))
                else Brush.linearGradient(listOf(Color.White.copy(.78f),Color(0xFFF4F1FA).copy(.82f)))
            )
            .border(1.dp,Color.White.copy(alpha=.72f),RoundedCornerShape(if(small)12.dp else 17.dp))
            .clickable(onClick=onClick),
        contentAlignment=Alignment.Center
    ){
        Text(text,fontSize=if(small)11.sp else if(text.length<=2)20.sp else 11.sp,fontWeight=FontWeight.Bold,color=Color(0xFF413748))
    }
}

@Composable
private fun NoticeDialog(
    controller:AppController,
    onConfirm:()->Unit,
    onAutoplay:(Boolean)->Unit,
    onLoop:(Boolean)->Unit
){
    val cover=rememberUriBitmap(controller.customCoverUri)
    AlertDialog(
        onDismissRequest={},
        containerColor=if(controller.darkTheme) Color(0xF2292430) else Color(0xF9FFF7FB),
        shape=RoundedCornerShape(28.dp),
        title={
            Row(verticalAlignment=Alignment.CenterVertically){
                if(cover!=null){
                    Image(cover,null,Modifier.size(54.dp).clip(RoundedCornerShape(17.dp)),contentScale=ContentScale.Crop)
                }else{
                    Image(painterResource(R.drawable.author_avatar),null,Modifier.size(54.dp).clip(RoundedCornerShape(17.dp)),contentScale=ContentScale.Crop)
                }
                Spacer(Modifier.width(10.dp))
                Column{
                    Text("S44 英雄战力工具",fontWeight=FontWeight.Bold,fontSize=18.sp)
                    Row{Text("作者 ",fontSize=11.sp);Text("秋天",fontSize=11.sp,color=AuthorGold,fontWeight=FontWeight.Bold)}
                }
            }
        },
        text={
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
                Text("公式精确优先  未公开数据只标估算  一切战力以游戏内最终显示为准",fontSize=12.sp,lineHeight=19.sp)
                SettingSwitch("下次启动自动播放",controller.musicAutoplay,onAutoplay)
                SettingSwitch("一直循环播放  三首按顺序循环",controller.musicLoop,onLoop)
            }
        },
        confirmButton={
            Button(
                onClick=onConfirm,
                colors=ButtonDefaults.buttonColors(containerColor=PerfPurple),
                shape=RoundedCornerShape(15.dp)
            ){Text("我知道了")}
        }
    )
}

@Composable
private fun MusicDock(
    controller:AppController,
    music:MusicEngine,
    expanded:Boolean,
    onTogglePanel:()->Unit,
    onPrevious:()->Unit,
    onPlayPause:()->Unit,
    onNext:()->Unit,
    onLoop:()->Unit
){
    Row(
        Modifier.fillMaxWidth().padding(end=6.dp),
        horizontalArrangement=Arrangement.End,
        verticalAlignment=Alignment.CenterVertically
    ){
        if(expanded){
            Row(
                Modifier.clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha=.78f))
                    .border(1.dp,Color.White.copy(.72f),RoundedCornerShape(22.dp))
                    .padding(7.dp),
                verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(5.dp)
            ){
                Text(music.currentName,fontSize=9.sp,fontWeight=FontWeight.Bold,modifier=Modifier.width(92.dp),maxLines=1,overflow=TextOverflow.Ellipsis)
                DockButton("⏮",onPrevious)
                DockButton(if(music.isPlaying)"Ⅱ" else "▶",onPlayPause)
                DockButton("⏭",onNext)
                DockButton(if(controller.musicLoop)"∞" else "1",onLoop)
            }
        }
        Spacer(Modifier.width(5.dp))
        Box(
            Modifier.size(62.dp).clip(RoundedCornerShape(22.dp))
                .background(Color.White.copy(alpha=.80f))
                .border(1.dp,Color.White.copy(alpha=.80f),RoundedCornerShape(22.dp))
                .clickable(onClick=onTogglePanel),
            contentAlignment=Alignment.Center
        ){Text("♫",fontSize=30.sp,color=Color(0xFF302A35))}
    }
}

@Composable
private fun DockButton(text:String,onClick:()->Unit){
    Box(Modifier.size(35.dp).clip(CircleShape).background(Color(0xFFF2EDF7)).clickable(onClick=onClick),contentAlignment=Alignment.Center){
        Text(text,fontSize=13.sp,fontWeight=FontWeight.Bold)
    }
}

private fun valueFor(c:AppController,id:String)=when(id){
    "targetPower"->c.targetPower
    "gamePower"->c.gamePower
    "rankStars"->c.rankStars
    "performance"->c.performance
    "performanceCap"->c.performanceCap
    "peakNow"->c.peakNow
    "peakUnlocked"->c.peakUnlocked
    "coefficientInput"->c.coefficientInput
    "curveText"->c.curveText
    "matchText"->c.matchText
    "matchCap"->c.matchCap
    "recycleRate"->c.recycleRate
    "pptAuthor"->c.pptAuthorInput
    else->""
}

private fun nextPadTarget(id:String):PadTarget? {
    val list=listOf(
        PadTarget("targetPower","目标英雄战力"),
        PadTarget("gamePower","当前游戏英雄战力"),
        PadTarget("rankStars","当前王者星数"),
        PadTarget("performance","当前表现分"),
        PadTarget("performanceCap","表现分上限"),
        PadTarget("peakNow","当前巅峰分"),
        PadTarget("peakUnlocked","英雄已解锁巅峰分"),
        PadTarget("coefficientInput","游戏显示巅峰系数"),
        PadTarget("curveText","巅峰分与系数映射表",true),
        PadTarget("matchText","45 场对局数据",true),
        PadTarget("matchCap","表现分上限"),
        PadTarget("recycleRate","周回收比例"),
        PadTarget("pptAuthor","作者 QQ 验证",false,true)
    )
    val i=list.indexOfFirst{it.id==id}
    return if(i>=0 && i<list.lastIndex)list[i+1] else null
}

@Composable
private fun rememberUriBitmap(uriString:String?):ImageBitmap?{
    val context=LocalContext.current
    var image by remember(uriString){mutableStateOf<ImageBitmap?>(null)}
    LaunchedEffect(uriString){
        image=if(uriString.isNullOrBlank())null else withContext(Dispatchers.IO){
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(uriString))?.use{
                    BitmapFactory.decodeStream(it)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    return image
}

private fun persistReadPermission(context:android.content.Context,uri:Uri){
    runCatching {
        context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

private fun queryName(context:android.content.Context,uri:Uri):String{
    var name=""
    context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{c->
        if(c.moveToFirst())name=c.getString(0)?:""
    }
    return name
}

private fun openUrl(context:android.content.Context,url:String){
    runCatching{
        context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private suspend fun checkOfficialStatus(c:AppController){
    c.officialStatus="检测官网连接"
    c.officialStatusOk=null
    val ok=withContext(Dispatchers.IO){
        runCatching{
            val conn=(URL("https://pvp.qq.com/").openConnection() as HttpURLConnection).apply{
                connectTimeout=3500
                readTimeout=3500
                requestMethod="HEAD"
                instanceFollowRedirects=true
            }
            try{conn.connect();conn.responseCode in 200..499}finally{conn.disconnect()}
        }.getOrDefault(false)
    }
    c.officialStatusOk=ok
    c.officialStatus=if(ok)"官网连接正常" else "官网连接待确认"
}

private fun timeStamp():String=SimpleDateFormat("yyyyMMdd_HHmm",Locale.US).format(Date())

private fun s44LightColors():ColorScheme=lightColorScheme(
    primary=PerfPurple,
    secondary=Color(0xFFE865A2),
    background=Color(0xFFF9EAF3),
    surface=Color(0xFFFFF8FC),
    onBackground=Color(0xFF3E3544),
    onSurface=Color(0xFF3E3544),
    onSurfaceVariant=Color(0xFF796E80),
    outline=Color(0xFF8E8094)
)

private fun s44DarkColors():ColorScheme=darkColorScheme(
    primary=Color(0xFFC5A7FF),
    secondary=Color(0xFFFF9CCB),
    background=Color(0xFF15131A),
    surface=Color(0xFF24202A),
    onBackground=Color(0xFFF5EEF7),
    onSurface=Color(0xFFF5EEF7),
    onSurfaceVariant=Color(0xFFC6BBC9),
    outline=Color(0xFF92869A)
)
