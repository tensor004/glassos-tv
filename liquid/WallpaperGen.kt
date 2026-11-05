
package io.github.abhishekkaisar.glassos

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/** Procedural scenes, drawn in a 1920x1080 design space and scaled to the target size. */
object WallpaperGen {
    private const val W = 1920f
    private const val H = 1080f

    fun render(scene: String, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(w / W, h / H)
        when (scene) {
            "ocean" -> ocean(c)
            "aurora" -> aurora(c, bmp)
            "mountain" -> snowMountain(c)
            "forest" -> forestMist(c)
            "milkyway" -> milkyWayScene(c)
            "coast" -> oceanCliff(c)
            "dark" -> dark(c)
            else -> desert(c)
        }
        return bmp
    }

    private fun paint() = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }

    private fun Random.f(): Float = nextFloat()
    private fun Random.range(a: Float, b: Float): Float = a + nextFloat() * (b - a)
    private fun Random.gauss(): Float = nextGaussian().toFloat()

    private fun alphaOf(color: Int, a: Float): Int =
        (color and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255).toInt() shl 24)

    private fun lerpColor(a: Int, b: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        fun ch(x: Int, y: Int, s: Int) =
            (((x shr s) and 255) * (1f - f) + ((y shr s) and 255) * f).toInt()
        return Color.argb(ch(a,b,24), ch(a,b,16), ch(a,b,8), ch(a,b,0))
    }

    private fun vGrad(
        c: Canvas, top: Float, bottom: Float, colors: IntArray,
        pos: FloatArray?, y0: Float = 0f, y1: Float = H
    ) {
        val p = paint()
        p.shader = LinearGradient(0f, top, 0f, bottom, colors, pos, Shader.TileMode.CLAMP)
        c.drawRect(0f, y0, W, y1, p)
    }

    private fun glow(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, a: Float, sy: Float = 1f) {
        val p = paint()
        p.shader = RadialGradient(
            0f, 0f, 1f,
            intArrayOf(alphaOf(color, a), alphaOf(color, a * 0.45f), alphaOf(color, 0f)),
            floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP
        )
        c.save()
        c.translate(cx, cy)
        c.scale(r, r * sy)
        c.drawCircle(0f, 0f, 1f, p)
        c.restore()
    }

    private val starTints = intArrayOf(
        Color.WHITE, 0xFFD6E2FF.toInt(),
        0xFFFFF1DC.toInt(), 0xFFE8EEFF.toInt()
    )

    private fun stars(c: Canvas, rnd: Random, count: Int, maxY: Float, bright: Int, fade: Float = 1f) {
        val p = paint()
        repeat(count) {
            val x = rnd.f() * W
            val y = maxY * rnd.f().pow(1.3f)
            val r = 0.5f + rnd.f().pow(6f) * 1.9f
            val horizonFade = 1f - (y / maxY).pow(3f) * 0.75f
            p.color = alphaOf(
                starTints[rnd.nextInt(starTints.size)],
                (0.22f + rnd.f() * 0.78f) * horizonFade * fade
            )
            c.drawCircle(x, y, r, p)
        }
        repeat(bright) {
            val x = rnd.f() * W
            val y = maxY * 0.85f * rnd.f().pow(1.2f)
            val tint = starTints[rnd.nextInt(starTints.size)]
            glow(c, x, y, rnd.range(8f, 18f), tint, 0.45f * fade)
            p.color = alphaOf(Color.WHITE, 0.95f * fade)
            c.drawCircle(x, y, rnd.range(1.4f, 2.3f), p)
            if (rnd.nextInt(3) == 0) {
                p.strokeWidth = 0.8f
                p.color = alphaOf(tint, 0.35f * fade)
                val l = rnd.range(9f, 16f)
                c.drawLine(x-l, y, x+l, y, p)
                c.drawLine(x, y-l, x, y+l, p)
            }
        }
    }

    private fun milkyWay(c: Canvas, rnd: Random, x0: Float, y0: Float, x1: Float, y1: Float, width: Float) {
        val dx = x1 - x0
        val dy = y1 - y0
        val len = hypot(dx, dy)
        val nx = -dy / len
        val ny = dx / len
        val cols = intArrayOf(
            0xFF9FB4FF.toInt(), 0xFFC9B8FF.toInt(),
            0xFFFFE2C8.toInt(), 0xFF8FA8E8.toInt()
        )
        fun at(t: Float, off: Float, out: FloatArray) {
            out[0] = x0 + dx * t + nx * off
            out[1] = y0 + dy * t + ny * off
        }
        val pt = FloatArray(2)
        repeat(240) {
            at(rnd.f(), rnd.gauss() * width * 0.75f, pt)
            glow(c, pt[0], pt[1], rnd.range(70f, 230f), cols[rnd.nextInt(cols.size)], rnd.range(0.025f, 0.06f))
        }
        repeat(120) {
            at(rnd.f(), rnd.gauss() * width * 0.3f, pt)
            glow(c, pt[0], pt[1], rnd.range(25f, 80f), 0xFFF2ECFF.toInt(), rnd.range(0.04f, 0.09f))
        }
        repeat(90) {
            at(rnd.f(), rnd.gauss() * width * 0.12f + width * 0.1f, pt)
            glow(c, pt[0], pt[1], rnd.range(18f, 60f), 0xFF060818.toInt(), rnd.range(0.18f, 0.35f), 0.6f)
        }
        val p = paint()
        repeat(2800) {
            at(rnd.f(), rnd.gauss() * width * 0.55f, pt)
            p.color = alphaOf(starTints[rnd.nextInt(starTints.size)], rnd.range(0.2f, 0.85f))
            c.drawCircle(pt[0], pt[1], 0.4f + rnd.f().pow(4f) * 1.1f, p)
        }
    }

    private fun moon(c: Canvas, cx: Float, cy: Float, r: Float) {
        glow(c, cx, cy, 560f, 0xFFA8B8FF.toInt(), 0.16f)
        glow(c, cx, cy, r * 4.5f, 0xFFDDE4FF.toInt(), 0.32f)
        val p = paint()
        p.shader = RadialGradient(
            cx-r*0.3f, cy-r*0.3f, r*1.4f,
            intArrayOf(0xFFFFFDF4.toInt(), 0xFFEDE8DA.toInt(), 0xFFD9D3C4.toInt()),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, r, p)
        p.shader = null
        p.color = 0x16000000
        c.drawCircle(cx-r*0.25f, cy-r*0.15f, r*0.32f, p)
        c.drawCircle(cx+r*0.3f, cy+r*0.25f, r*0.22f, p)
        c.drawCircle(cx+r*0.05f, cy+r*0.45f, r*0.16f, p)
    }

    private fun desert(c: Canvas) {
        val rnd = Random(42)
        vGrad(c, 0f, H*0.70f,
            intArrayOf(0xFF02041A.toInt(),0xFF060C33.toInt(),0xFF101A52.toInt(),0xFF232C6C.toInt(),0xFF4B3D7C.toInt(),0xFF9A5E78.toInt()),
            floatArrayOf(0f,0.22f,0.45f,0.66f,0.84f,1f))
        milkyWay(c,rnd,-200f,780f,2150f,-200f,150f)
        stars(c,rnd,2300,H*0.66f,28)
        moon(c,1460f,290f,28f)
        glow(c,W*0.40f,H*0.64f,W*0.75f,0xFFFF9A5A.toInt(),0.32f,0.30f)
        glow(c,W*0.72f,H*0.62f,W*0.45f,0xFFFF7A6A.toInt(),0.18f,0.28f)
        dune(c,rnd,H*0.63f,46f,330f,0xFFB86A4C.toInt(),0xFF6E3328.toInt(),0xFF8A6A9A.toInt(),0.40f,0.35f)
        dune(c,rnd,H*0.725f,90f,520f,0xFFD87A42.toInt(),0xFF7E3520.toInt(),0xFF9A6A8A.toInt(),0.22f,0.45f)
        dune(c,rnd,H*0.85f,130f,700f,0xFFF09250.toInt(),0xFF8E3C1A.toInt(),0xFFB0708A.toInt(),0.08f,0.55f)
        dune(c,rnd,H*0.99f,110f,900f,0xFFD2702F.toInt(),0xFF5A200C.toInt(),0,0f,0.55f)
    }

    private fun dune(c: Canvas, rnd: Random, baseY: Float, amp: Float, lambda: Float, top: Int, bottom: Int, haze: Int, hazeA: Float, shade: Float) {
        val step = 3f
        val n = (W/step).toInt()+3
        val xs = FloatArray(n)
        val ys = FloatArray(n)
        val ph = rnd.f()*10f
        val ph2 = rnd.f()*10f
        var minY = H
        for (i in 0 until n) {
            val x = i*step
            val warp = x + lambda*0.28f*sin(x/(lambda*1.3f)+ph2) + 30f*sin(x/150f+ph)
            var u = warp/lambda+ph
            u -= kotlin.math.floor(u)
            val k = 0.68f
            val crest = if (u<k) (u/k).pow(1.7f) else (1f-(u-k)/(1f-k)).pow(1.35f)
            val swell = 0.55f+0.45f*sin(x/(lambda*1.7f)+ph2*0.7f)
            xs[i]=x
            ys[i]=baseY-amp*crest*swell-0.3f*amp*sin(x/1300f+ph)
            if (ys[i]<minY) minY=ys[i]
        }
        val path=Path()
        path.moveTo(0f,H)
        for(i in 0 until n) path.lineTo(xs[i],ys[i])
        path.lineTo(W,H)
        path.close()
        val depth=(baseY-minY)+amp*1.6f+60f
        val p=paint()
        p.shader=LinearGradient(0f,minY,0f,minY+depth,intArrayOf(top,lerpColor(top,bottom,0.5f),bottom),floatArrayOf(0f,0.45f,1f),Shader.TileMode.CLAMP)
        c.drawPath(path,p)
        if (haze != 0) {
            glow(c,W*0.5f,baseY, W*0.8f,haze,hazeA,0.18f)
        }
    }

    private fun ocean(c: Canvas) {
        val rnd=Random(21)
        val hz=H*0.54f
        vGrad(c,0f,hz,intArrayOf(0xFF151A58.toInt(),0xFF4B4B9B.toInt(),0xFFFFB6A0.toInt(),0xFFFFE0B5.toInt()),floatArrayOf(0f,0.42f,0.82f,1f),0f,hz)
        val sunX=W*0.50f
        val p=paint()
        p.shader=RadialGradient(sunX,hz,240f,intArrayOf(0xFFFFF3D8.toInt(),0xFFFFD3A0.toInt(),0x00FFD3A0),null,Shader.TileMode.CLAMP)
        c.drawCircle(sunX,hz+10f,66f,p)
        p.shader=null
        repeat(16) {
            val y=rnd.range(H*0.30f,H*0.58f)
            val col=if(rnd.nextBoolean()) 0xFFFFA4A0.toInt() else 0xFFFFC4A0.toInt()
            glow(c,rnd.range(0f,W),y,rnd.range(220f,560f),col,rnd.range(0.10f,0.22f),rnd.range(0.025f,0.05f))
        }
        vGrad(c,hz,H,intArrayOf(0xFF8A5A86.toInt(),0xFF3A2E66.toInt(),0xFF141A44.toInt(),0xFF060A20.toInt()),floatArrayOf(0f,0.22f,0.6f,1f),hz,H)
        val lp=paint()
        repeat(700) {
            val d=rnd.f().pow(1.5f)
            val y=hz+2f+d*(H-hz)
            val x=rnd.f()*W
            lp.color=alphaOf(0xFF9AB0FF.toInt(),rnd.range(0.03f,0.08f)*(1f-d*0.5f))
            val l=rnd.range(20f,60f+d*220f)
            c.drawRect(x,y,x+l,y+1f+d*2.5f,lp)
        }
        repeat(1100) {
            val d=rnd.f().pow(1.6f)
            val y=hz+3f+d*(H-hz)
            val spread=30f+d*460f
            val x=sunX+rnd.gauss()*spread
            val l=rnd.range(10f,40f+d*170f)
            lp.color=alphaOf(0xFFFFD49A.toInt(),(1f-d)*0.65f*rnd.range(0.3f,1f))
            c.drawRect(x-l/2,y,x+l/2,y+1.2f+d*3.5f,lp)
        }
        lp.color=alphaOf(0xFFFFD0A0.toInt(),0.45f)
        c.drawRect(0f,hz-1f,W,hz+1.5f,lp)
    }

    private fun aurora(c: Canvas, bmp: Bitmap) {
        val rnd=Random(23)
        val water=H*0.84f
        vGrad(c,0f,water,intArrayOf(0xFF01030A.toInt(),0xFF020C1C.toInt(),0xFF052234.toInt(),0xFF0A3644.toInt()),floatArrayOf(0f,0.35f,0.75f,1f),0f,water)
        stars(c,rnd,1700,H*0.72f,18)
        curtain(c,H*0.36f,260f,0f,0xFF3DFFB0.toInt(),0xFF9B5CFF.toInt(),1f)
        curtain(c,H*0.30f,200f,2.1f,0xFF4DE8FF.toInt(),0xFFFF6AD5.toInt(),0.6f)
        repeat(36) {
            val x=rnd.f()*W
            glow(c,x,H*0.42f+rnd.gauss()*60f,rnd.range(120f,260f),0xFF3DFFB0.toInt(),0.06f,0.6f)
        }
        mountains(c,rnd,H*0.72f,120f,0xFF071822.toInt(),water)
        mountains(c,rnd,H*0.79f,70f,0xFF02080D.toInt(),water)

        val sy=bmp.height/H
        val wy=(water*sy).toInt()
        val wh=bmp.height-wy
        if(wh>0 && wy-wh>=0) {
            val src=Bitmap.createBitmap(bmp,0,wy-wh,bmp.width,wh)
            c.save()
            c.setMatrix(null)
            val m=Matrix()
            m.setScale(1f,-1f)
            m.postTranslate(0f,(wy+wh).toFloat())
            c.drawBitmap(src,m,Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha=120 })
            c.restore()
            src.recycle()
        }
        val p=paint()
        p.shader=LinearGradient(0f,water,0f,H,intArrayOf(0x55000610,0xDD00040A.toInt()),null,Shader.TileMode.CLAMP)
        c.drawRect(0f,water,W,H,p)
        p.shader=null
        repeat(160) {
            val y=water+rnd.f().pow(1.4f)*(H-water)
            val x=rnd.f()*W
            p.color=alphaOf(0xFF8CFFD8.toInt(),rnd.range(0.03f,0.09f))
            c.drawRect(x,y,x+rnd.range(30f,180f),y+1.2f,p)
        }
    }

    private fun curtain(c: Canvas, baseY: Float, height: Float, ph: Float, low: Int, high: Int, strength: Float) {
        val p=paint()
        var x=-10f
        while(x<W+10f) {
            val by=baseY+95f*sin(x/270f+ph)+32f*sin(x/83f+ph*2f)+120f
            val hgt=height+90f*sin(x/190f+0.5f+ph)
            val k=0.35f+0.65f*sin(x/140f+0.3f+ph).pow(2)*(0.6f+0.4f*sin(x/37f+ph))
            val a=(k*strength).coerceIn(0f,1f)
            p.shader=LinearGradient(0f,by-hgt,0f,by+24f,intArrayOf(alphaOf(high,0f),alphaOf(high,0.10f*a),alphaOf(low,0.22f*a),alphaOf(low,0.42f*a),alphaOf(low,0f)),floatArrayOf(0f,0.25f,0.7f,0.92f,1f),Shader.TileMode.CLAMP)
            c.drawRect(x,by-hgt,x+3.5f,by+24f,p)
            x+=3f
        }
    }

    private fun mountains(c: Canvas, rnd: Random, baseY: Float, amp: Float, color: Int, floor: Float) {
        val path=Path()
        path.moveTo(0f,floor)
        var x=0f
        var y=baseY
        val ph=rnd.f()*6f
        while(x<=W+20f) {
            val target=baseY-amp*(0.5f+0.5f*sin(x/260f+ph))*(0.6f+0.4f*sin(x/610f+ph*1.7f))
            y+=(target-y)*0.45f+rnd.gauss()*7f
            path.lineTo(x,y)
            x+=rnd.range(10f,26f)
        }
        path.lineTo(W,floor)
        path.close()
        val p=paint()
        p.color=color
        c.drawPath(path,p)
    }

    private fun snowMountain(c: Canvas) {
        val rnd=Random(71)
        vGrad(c,0f,H,intArrayOf(0xFF07152F.toInt(),0xFF233D68.toInt(),0xFF9A8E9C.toInt(),0xFFF2B99A.toInt()),floatArrayOf(0f,0.42f,0.76f,1f))
        glow(c,W*0.72f,H*0.53f,620f,0xFFFFB77C.toInt(),0.34f,0.42f)
        stars(c,rnd,500,H*0.38f,5,0.55f)
        mountains(c,rnd,H*0.72f,300f,0xFF263B58.toInt(),H)
        val peaks=Path().apply {
            moveTo(0f,H); lineTo(0f,H*.68f); lineTo(W*.13f,H*.42f); lineTo(W*.21f,H*.59f)
            lineTo(W*.36f,H*.30f); lineTo(W*.48f,H*.56f); lineTo(W*.64f,H*.35f); lineTo(W*.78f,H*.62f)
            lineTo(W*.91f,H*.40f); lineTo(W,H*.58f); lineTo(W,H); close()
        }
        val p=paint().apply { shader=LinearGradient(0f,H*.30f,0f,H,intArrayOf(0xFFE7EAF0.toInt(),0xFF879BB5.toInt(),0xFF263B57.toInt()),null,Shader.TileMode.CLAMP) }
        c.drawPath(peaks,p)
        val snow=paint().apply { color=0xFFF7F4EA.toInt() }
        val cap=Path().apply {
            moveTo(W*.13f,H*.42f); lineTo(W*.105f,H*.48f); lineTo(W*.16f,H*.46f); lineTo(W*.21f,H*.59f)
            lineTo(W*.36f,H*.30f); lineTo(W*.33f,H*.40f); lineTo(W*.38f,H*.39f); lineTo(W*.48f,H*.56f)
            lineTo(W*.64f,H*.35f); lineTo(W*.61f,H*.44f); lineTo(W*.67f,H*.46f); lineTo(W*.78f,H*.62f)
            lineTo(W*.91f,H*.40f); lineTo(W*.88f,H*.48f); lineTo(W*.94f,H*.48f); lineTo(W,H*.58f)
        }
        c.drawPath(cap,snow)
        vGrad(c,H*.78f,H,intArrayOf(0xFF172C43.toInt(),0xFF071522.toInt()),null,H*.78f,H)
    }

    private fun forestMist(c: Canvas) {
        val rnd=Random(72)
        vGrad(c,0f,H,intArrayOf(0xFF14263B.toInt(),0xFF607D8A.toInt(),0xFFB5C3C0.toInt(),0xFF334C4C.toInt()),floatArrayOf(0f,.38f,.68f,1f))
        glow(c,W*.48f,H*.55f,850f,0xFFE4E5D4.toInt(),.34f,.5f)
        fun tree(x: Float, base: Float, height: Float, color: Int, width: Float) {
            val p=paint().apply { this.color=color }
            val path=Path().apply {
                moveTo(x,base-height); lineTo(x-width*.55f,base-height*.42f)
                lineTo(x-width*.22f,base-height*.48f); lineTo(x-width*.72f,base-height*.12f)
                lineTo(x-width*.28f,base-height*.18f); lineTo(x-width,base)
                lineTo(x+width,base); lineTo(x+width*.28f,base-height*.18f)
                lineTo(x+width*.72f,base-height*.12f); lineTo(x+width*.22f,base-height*.48f)
                lineTo(x+width*.55f,base-height*.42f); close()
            }
            c.drawPath(path,p)
        }
        repeat(42) { tree(rnd.f()*W,H*.83f+rnd.f()*70f,180f+rnd.f()*220f,0x88304750.toInt(),35f+rnd.f()*35f) }
        repeat(30) { tree(rnd.f()*W,H*1.02f,260f+rnd.f()*300f,0xFF102B2D.toInt(),45f+rnd.f()*55f) }
        val fog=paint().apply { shader=LinearGradient(0f,H*.48f,0f,H*.94f,intArrayOf(0x00DDE4DF,0x88DDE4DF.toInt(),0x00DDE4DF),null,Shader.TileMode.CLAMP) }
        c.drawRect(0f,H*.48f,W,H,fog)
    }

    private fun milkyWayScene(c: Canvas) {
        val rnd=Random(73)
        vGrad(c,0f,H,intArrayOf(0xFF020716.toInt(),0xFF101A3A.toInt(),0xFF28304E.toInt(),0xFF080E1B.toInt()),floatArrayOf(0f,.48f,.78f,1f))
        milkyWay(c,rnd,-180f,H*.82f,W*1.08f,-H*.18f,210f)
        stars(c,rnd,3300,H*.88f,35)
        mountains(c,rnd,H*.79f,150f,0xFF111C2D.toInt(),H)
        mountains(c,rnd,H*.88f,80f,0xFF050B16.toInt(),H)
    }

    private fun oceanCliff(c: Canvas) {
        val rnd=Random(74)
        vGrad(c,0f,H*.62f,intArrayOf(0xFF6C8FAE.toInt(),0xFFFFC18B.toInt(),0xFFFFD7A2.toInt()),floatArrayOf(0f,.62f,1f),0f,H*.62f)
        glow(c,W*.73f,H*.48f,560f,0xFFFFE1AE.toInt(),.65f,.55f)
        vGrad(c,H*.58f,H,intArrayOf(0xFF2D9FA7.toInt(),0xFF126C80.toInt(),0xFF07334D.toInt()),floatArrayOf(0f,.35f,1f),H*.58f,H)
        val cliff=Path().apply {
            moveTo(0f,H*.42f); lineTo(W*.18f,H*.48f); lineTo(W*.31f,H*.63f)
            lineTo(W*.42f,H*.67f); lineTo(W*.54f,H*.83f); lineTo(W*.70f,H*.89f)
            lineTo(W*.78f,H); lineTo(0f,H); close()
        }
        val p=paint().apply { shader=LinearGradient(0f,H*.4f,W*.4f,H,intArrayOf(0xFFB77B58.toInt(),0xFF6E5147.toInt(),0xFF1D3440.toInt()),null,Shader.TileMode.CLAMP) }
        c.drawPath(cliff,p)
        val edge=paint().apply { color=0xFFE9C19A.toInt(); strokeWidth=5f; style=Paint.Style.STROKE }
        c.drawLine(0f,H*.42f,W*.18f,H*.48f,edge)
        repeat(420) {
            val x=rnd.f()*W
            val y=H*.62f+rnd.f()*H*.34f
            p.shader=null
            p.color=alphaOf(0xFFB5F4E8.toInt(),rnd.range(.025f,.11f))
            c.drawLine(x,y,x+rnd.range(12f,85f),y,p)
        }
    }

    private fun dark(c: Canvas) {
        vGrad(c,0f,H,intArrayOf(0xFF0B0D18.toInt(),0xFF07080F.toInt()),null)
        glow(c,W*.16f,H*.12f,1050f,0xFF3048B0.toInt(),0.55f)
        glow(c,W*.88f,H*.26f,820f,0xFF5A2A9A.toInt(),0.45f)
        glow(c,W*.62f,H*1.05f,1000f,0xFF105A8A.toInt(),0.40f)
        glow(c,W*.40f,H*.60f,600f,0xFF203060.toInt(),0.25f)
    }
}
