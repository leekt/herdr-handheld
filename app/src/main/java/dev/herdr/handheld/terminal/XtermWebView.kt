package dev.herdr.handheld.terminal

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.*
import androidx.webkit.*
import dev.herdr.handheld.herdr.TerminalFrame
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.ByteArrayInputStream
import java.util.Base64

@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
class XtermWebView(context: Context, private val viewport: (Int,Int,Long)->Unit, private val fault: (String)->Unit) : WebView(context), TerminalRenderer {
    private val origin = "https://appassets.androidplatform.net"
    private val ready = CompletableDeferred<Unit>()
    private var resetAck: CompletableDeferred<Unit>? = null
    private var writeAck: CompletableDeferred<Unit>? = null
    private var generation = -1L
    private var expectedSeq = -1L
    var applicationCursor = false; private set
    var bracketedPaste = false; private set

    init {
        layoutParams=android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT,android.view.ViewGroup.LayoutParams.MATCH_PARENT)
        setBackgroundColor(android.graphics.Color.BLACK)
        isFocusable = false
        isFocusableInTouchMode = false
        settings.apply {
            javaScriptEnabled=true;domStorageEnabled=false;databaseEnabled=false
            allowFileAccess=false;allowContentAccess=false
            mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false);javaScriptCanOpenWindowsAutomatically=false
            mediaPlaybackRequiresUserGesture=true;setGeolocationEnabled(false)
            cacheMode=WebSettings.LOAD_NO_CACHE
            blockNetworkLoads=true
        }
        val loader=WebViewAssetLoader.Builder().addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context)).build()
        webViewClient=object: WebViewClientCompat() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest)=true
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                val uri=request.url
                if(uri.scheme=="https" && uri.host=="appassets.androidplatform.net" && uri.port == -1 && uri.path?.startsWith("/assets/terminal/")==true)
                    return loader.shouldInterceptRequest(uri) ?: blocked()
                return blocked()
            }
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                writeAck?.cancel();resetAck?.cancel();fault("Terminal renderer stopped. Reopen the target.");return true
            }
        }
        webChromeClient=object: WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message)=false
            override fun onConsoleMessage(message: ConsoleMessage)=true
        }
        setDownloadListener { _,_,_,_,_ -> }
        if(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(this,"Handheld",setOf(origin)) { _,message,source,isMainFrame,_ ->
                if(!isMainFrame || source.toString().trimEnd('/')!=origin || (message.data?.length ?: 0)>2048) return@addWebMessageListener
                runCatching {
                    val obj=Json.parseToJsonElement(message.data ?: "").jsonObject
                    val kind=obj["type"]?.jsonPrimitive?.content
                    if(kind=="ready") { ready.complete(Unit);return@runCatching }
                    val g=obj["generation"]?.jsonPrimitive?.longOrNull ?: return@runCatching
                    if(g!=generation) return@runCatching
                    when(kind) {
                        "reset" -> resetAck?.complete(Unit)
                        "viewport" -> {
                            val cols=obj["cols"]?.jsonPrimitive?.intOrNull ?: 0
                            val rows=obj["rows"]?.jsonPrimitive?.intOrNull ?: 0
                            if(cols in 2..500 && rows in 2..300) viewport(cols,rows,g)
                        }
                        "written" -> if(obj["seq"]?.jsonPrimitive?.longOrNull==expectedSeq) {
                            applicationCursor=obj["applicationCursor"]?.jsonPrimitive?.booleanOrNull ?: false
                            bracketedPaste=obj["bracketedPaste"]?.jsonPrimitive?.booleanOrNull ?: false
                            writeAck?.complete(Unit)
                        }
                    }
                }
            }
            loadUrl("$origin/assets/terminal/index.html")
        } else { ready.completeExceptionally(IllegalStateException("WebView messaging unsupported"));fault("Update Android System WebView to use the terminal.") }
    }
    private fun blocked()=WebResourceResponse("text/plain","UTF-8",403,"Blocked",mapOf("Cache-Control" to "no-store"),ByteArrayInputStream(byteArrayOf()))
    private fun post(obj: JsonObject) { postWebMessage(WebMessage(obj.toString()),Uri.parse(origin)) }
    override suspend fun reset(generation: Long, fontSize: Int) = withContext(Dispatchers.Main) {
        withTimeout(6000) { ready.await() }
        this@XtermWebView.generation=generation
        applicationCursor=false;bracketedPaste=false
        writeAck?.cancel();resetAck?.cancel()
        val ack=CompletableDeferred<Unit>();resetAck=ack
        post(buildJsonObject { put("type","reset");put("generation",generation);put("fontSize",fontSize) })
        withTimeout(3000) { ack.await() }
    }
    override suspend fun render(frame: TerminalFrame, generation: Long) = withContext(Dispatchers.Main) {
        if(generation!=this@XtermWebView.generation) return@withContext
        val ack=CompletableDeferred<Unit>();writeAck=ack;expectedSeq=frame.seq
        post(buildJsonObject { put("type","frame");put("generation",generation);put("seq",frame.seq)
            put("width",frame.width);put("height",frame.height);put("full",frame.full)
            put("bytes",Base64.getEncoder().encodeToString(frame.bytes)) })
        // Exactly one outstanding write; stalled renderers trigger a new observer snapshot.
        withTimeout(3000) { ack.await() }
    }
    override fun scroll(lines: Int, generation: Long) { post(buildJsonObject { put("type","scroll");put("generation",generation);put("lines",lines) }) }
    override fun font(size: Int, generation: Long) { post(buildJsonObject { put("type","font");put("generation",generation);put("size",size) }) }
    override fun destroy() { writeAck?.cancel();resetAck?.cancel();super.destroy() }
}
