package com.html2mp4.renderer.engine

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Eliminates Surface.lockCanvas() stride shear/green artifacts and VirtualDisplay timestamp drift
 * by rendering WebView (or decoded Video) frames onto a clean RGBA EGLWindowSurface tied to MediaCodec.
 */
class EglSurfaceRenderer {

    private val glThread = HandlerThread("HTML2MP4-GL").apply { start() }
    private val glHandler = Handler(glThread.looper)

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    private var surfaceWidth = 1280
    private var surfaceHeight = 720

    private var programOes = 0
    private var program2d = 0
    private var oesTexId = 0
    private var tex2dId = 0
    private var overlayTexId = 0

    private var oesSurfaceTexture: SurfaceTexture? = null
    var oesInputSurface: Surface? = null
        private set

    private val frameSync = Object()
    private val hasNewOesFrame = AtomicBoolean(false)
    private val oesFramesReceived = AtomicInteger(0)
    private var lastDrawnWasOes = false
    private var hasAny2dTexture = false
    @Volatile private var hasOverlayBitmap = false

    private val stMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }
    private val identityMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }

    private val vertexCoords = floatArrayOf(
        -1f, -1f,
        1f, -1f,
        -1f, 1f,
        1f, 1f
    )
    private val texCoordsOes = floatArrayOf(
        0f, 0f,
        1f, 0f,
        0f, 1f,
        1f, 1f
    )
    private val texCoordsBitmap = floatArrayOf(
        0f, 1f,
        1f, 1f,
        0f, 0f,
        1f, 0f
    )

    private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(vertexCoords.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(vertexCoords); position(0) }
    private val texOesBuffer: FloatBuffer = ByteBuffer.allocateDirect(texCoordsOes.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(texCoordsOes); position(0) }
    private val tex2dBuffer: FloatBuffer = ByteBuffer.allocateDirect(texCoordsBitmap.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(texCoordsBitmap); position(0) }

    fun prepare(encoderSurface: Surface, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        runSyncOnGl {
            initEgl(encoderSurface)
            initGlObjects(width, height)
        }
    }

    fun discardPendingOesFrames() {
        runSyncOnGl {
            val st = oesSurfaceTexture ?: return@runSyncOnGl
            hasNewOesFrame.set(false)
            runCatching {
                st.updateTexImage()
                st.getTransformMatrix(stMatrix)
            }
        }
    }

    fun drawOesFrame(ptsNs: Long, waitMs: Long = 14L): Boolean {
        if (!hasNewOesFrame.get() && waitMs > 0L) {
            synchronized(frameSync) {
                if (!hasNewOesFrame.get()) {
                    runCatching { frameSync.wait(waitMs) }
                }
            }
        }
        var rendered = false
        runSyncOnGl {
            val st = oesSurfaceTexture ?: return@runSyncOnGl
            if (hasNewOesFrame.compareAndSet(true, false)) {
                runCatching {
                    st.updateTexImage()
                    st.getTransformMatrix(stMatrix)
                    oesFramesReceived.incrementAndGet()
                }
            }
            if (oesFramesReceived.get() > 0) {
                renderOesQuad(ptsNs)
                lastDrawnWasOes = true
                rendered = true
            }
        }
        return rendered
    }

    fun drawBitmapFrame(bitmap: Bitmap, ptsNs: Long) {
        runSyncOnGl {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex2dId)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            hasAny2dTexture = true
            lastDrawnWasOes = false
            render2dQuad(ptsNs)
        }
    }

    fun repeatLastFrame(ptsNs: Long): Boolean {
        var didDraw = false
        runSyncOnGl {
            if (lastDrawnWasOes && oesFramesReceived.get() > 0) {
                renderOesQuad(ptsNs)
                didDraw = true
            } else if (hasAny2dTexture) {
                render2dQuad(ptsNs)
                didDraw = true
            }
        }
        return didDraw
    }

    fun setOverlayBitmap(bitmap: Bitmap?) {
        runSyncOnGl {
            if (bitmap == null) {
                hasOverlayBitmap = false
            } else {
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTexId)
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
                hasOverlayBitmap = true
            }
        }
    }

    fun hasOesFrames(): Boolean = oesFramesReceived.get() > 0

    fun release() {
        runSyncOnGl {
            runCatching { oesInputSurface?.release() }
            runCatching { oesSurfaceTexture?.release() }
            oesInputSurface = null
            oesSurfaceTexture = null

            if (programOes != 0) GLES20.glDeleteProgram(programOes)
            if (program2d != 0) GLES20.glDeleteProgram(program2d)
            if (oesTexId != 0 || tex2dId != 0 || overlayTexId != 0) {
                GLES20.glDeleteTextures(3, intArrayOf(oesTexId, tex2dId, overlayTexId), 0)
            }

            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
                if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglReleaseThread()
                EGL14.eglTerminate(eglDisplay)
            }
            eglSurface = EGL14.EGL_NO_SURFACE
            eglContext = EGL14.EGL_NO_CONTEXT
            eglDisplay = EGL14.EGL_NO_DISPLAY
        }
        glThread.quitSafely()
    }

    private fun runSyncOnGl(block: () -> Unit) {
        if (Thread.currentThread() === glThread) {
            block()
            return
        }
        val latch = CountDownLatch(1)
        var err: Throwable? = null
        val posted = glHandler.post {
            try {
                block()
            } catch (t: Throwable) {
                err = t
            } finally {
                latch.countDown()
            }
        }
        if (posted) {
            latch.await()
            err?.let { throw it }
        }
    }

    private fun initEgl(encoderSurface: Surface) {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

        val recordableAttr = 0x3142 // EGL_RECORDABLE_ANDROID
        val attribsWithRecordable = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            recordableAttr, 1,
            EGL14.EGL_NONE
        )
        val attribsFallback = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_NONE
        )

        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(eglDisplay, attribsWithRecordable, 0, configs, 0, 1, numConfigs, 0) || numConfigs[0] == 0) {
            EGL14.eglChooseConfig(eglDisplay, attribsFallback, 0, configs, 0, 1, numConfigs, 0)
        }
        val chosenConfig = configs[0] ?: error("EGLConfig tidak tersedia")

        val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(eglDisplay, chosenConfig, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
        val surfAttribs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, chosenConfig, encoderSurface, surfAttribs, 0)
        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
    }

    private fun initGlObjects(width: Int, height: Int) {
        val textures = IntArray(3)
        GLES20.glGenTextures(3, textures, 0)
        oesTexId = textures[0]
        tex2dId = textures[1]
        overlayTexId = textures[2]

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        listOf(tex2dId, overlayTexId).forEach { tid ->
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tid)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        }

        oesSurfaceTexture = SurfaceTexture(oesTexId).apply {
            setDefaultBufferSize(width, height)
            setOnFrameAvailableListener({
                hasNewOesFrame.set(true)
                synchronized(frameSync) { frameSync.notifyAll() }
            }, glHandler)
        }
        oesInputSurface = Surface(oesSurfaceTexture)

        val vsSource = """
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            uniform mat4 uTexMatrix;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = (uTexMatrix * aTexCoord).xy;
            }
        """.trimIndent()

        val fsOesSource = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTexCoord;
            uniform samplerExternalOES uTexture;
            void main() {
                gl_FragColor = texture2D(uTexture, vTexCoord);
            }
        """.trimIndent()

        val fs2dSource = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D uTexture;
            void main() {
                gl_FragColor = texture2D(uTexture, vTexCoord);
            }
        """.trimIndent()

        programOes = createProgram(vsSource, fsOesSource)
        program2d = createProgram(vsSource, fs2dSource)
    }

    private fun renderOesQuad(ptsNs: Long) {
        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glUseProgram(programOes)
        val posLoc = GLES20.glGetAttribLocation(programOes, "aPosition")
        val texLoc = GLES20.glGetAttribLocation(programOes, "aTexCoord")
        val matLoc = GLES20.glGetUniformLocation(programOes, "uTexMatrix")

        vertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        texOesBuffer.position(0)
        GLES20.glEnableVertexAttribArray(texLoc)
        GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 0, texOesBuffer)

        GLES20.glUniformMatrix4fv(matLoc, 1, false, stMatrix, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexId)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        if (hasOverlayBitmap) {
            drawAlphaOverlayQuad()
        }

        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, ptsNs)
        EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    private fun drawAlphaOverlayQuad() {
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUseProgram(program2d)
        val posLoc = GLES20.glGetAttribLocation(program2d, "aPosition")
        val texLoc = GLES20.glGetAttribLocation(program2d, "aTexCoord")
        val matLoc = GLES20.glGetUniformLocation(program2d, "uTexMatrix")

        vertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        tex2dBuffer.position(0)
        GLES20.glEnableVertexAttribArray(texLoc)
        GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 0, tex2dBuffer)

        GLES20.glUniformMatrix4fv(matLoc, 1, false, identityMatrix, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTexId)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun render2dQuad(ptsNs: Long) {
        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
        GLES20.glClearColor(1f, 1f, 1f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glUseProgram(program2d)
        val posLoc = GLES20.glGetAttribLocation(program2d, "aPosition")
        val texLoc = GLES20.glGetAttribLocation(program2d, "aTexCoord")
        val matLoc = GLES20.glGetUniformLocation(program2d, "uTexMatrix")

        vertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        tex2dBuffer.position(0)
        GLES20.glEnableVertexAttribArray(texLoc)
        GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 0, tex2dBuffer)

        GLES20.glUniformMatrix4fv(matLoc, 1, false, identityMatrix, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex2dId)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, ptsNs)
        EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    private fun createProgram(vs: String, fs: String): Int {
        val vShader = compileShader(GLES20.GL_VERTEX_SHADER, vs)
        val fShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fs)
        return GLES20.glCreateProgram().also { prog ->
            GLES20.glAttachShader(prog, vShader)
            GLES20.glAttachShader(prog, fShader)
            GLES20.glLinkProgram(prog)
            GLES20.glDeleteShader(vShader)
            GLES20.glDeleteShader(fShader)
        }
    }

    private fun compileShader(type: Int, src: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, src)
            GLES20.glCompileShader(shader)
        }
    }
}
