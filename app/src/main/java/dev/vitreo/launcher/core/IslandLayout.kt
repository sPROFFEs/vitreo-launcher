package dev.vitreo.launcher.core

/** Aoede-compatible dp controls. -1 follows the capsule / safe touch position. */
data class IslandLayout(
    val width: Int = 156, val height: Int = 48, val top: Int = 8, val horizontal: Int = 0,
    val handleWidth: Int = 32, val handleHeight: Int = 12, val handleTop: Int = -1, val handleHorizontal: Int = 0,
    val touchWidth: Int = 156, val touchHeight: Int = 48, val touchTop: Int = -1, val touchHorizontal: Int = 0,
    val coexistence: Int = 0, // 0: below other island, 1: pause, 2: independent
    val reserveAlways: Boolean = false, val reservedBottom: Int = 112, val duration: Int = 6,
) {
    fun sanitized() = copy(width=width.coerceIn(112,320),height=height.coerceIn(28,72),top=top.coerceIn(0,240),horizontal=horizontal.coerceIn(-100,100),
        handleWidth=handleWidth.coerceIn(16,128),handleHeight=handleHeight.coerceIn(0,32),handleTop=handleTop.coerceIn(-1,320),handleHorizontal=handleHorizontal.coerceIn(-100,100),
        touchWidth=touchWidth.coerceIn(48,320),touchHeight=touchHeight.coerceIn(48,160),touchTop=touchTop.coerceIn(-1,320),touchHorizontal=touchHorizontal.coerceIn(-100,100),
        coexistence=coexistence.coerceIn(0,2),reservedBottom=reservedBottom.coerceIn(0,400),duration=duration.coerceIn(3,15))
}

data class IslandRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right-left
    val height get() = bottom-top
    fun shift(y: Int) = copy(top=top+y,bottom=bottom+y)
}
data class IslandBounds(val capsule: IslandRect, val handle: IslandRect?, val touch: IslandRect, val window: IslandRect)

/** Full display coordinates; only the interaction region must clear the camera/status band. */
fun islandBounds(layout: IslandLayout, width: Int, height: Int, safeLeft: Int, safeTop: Int, safeRight: Int, safeBottom: Int,
    peerActive: Boolean = false): IslandBounds? {
    if (width < 64 || height < 80) return null
    val a=layout.sanitized()
    val reserve=peerActive || a.reserveAlways
    if(reserve && a.coexistence==1) return null
    val left=(safeLeft+8).coerceIn(0,width-48)
    val right=(width-safeRight-8).coerceIn(left+1,width)
    val bottom=(height-safeBottom-8).coerceAtLeast(1)
    fun rect(w:Int,h:Int,t:Int,x:Int):IslandRect {
        val rw=w.coerceAtMost(right-left); val rh=h.coerceAtMost(bottom)
        val px=left+(right-left-rw)*(x+100)/200
        val py=t.coerceIn(0,(bottom-rh).coerceAtLeast(0))
        return IslandRect(px,py,px+rw,py+rh)
    }
    val touchTop=maxOf(a.top,safeTop+4)
    var capsule=rect(a.width,a.height,a.top,a.horizontal)
    var touch=rect(a.touchWidth,a.touchHeight,maxOf(if(a.touchTop<0) touchTop else a.touchTop,safeTop+4),a.touchHorizontal)
    var handle=if(a.handleHeight==0) null else rect(a.handleWidth,a.handleHeight,
        if(a.handleTop<0) maxOf(a.top+a.height,safeTop+4)+4 else a.handleTop,a.handleHorizontal)
    val all=listOfNotNull(capsule,touch,handle)
    if(reserve && a.coexistence==0) {
        val shift=(a.reservedBottom+8-all.minOf{it.top}).coerceAtLeast(0)
        if(all.maxOf{it.bottom}+shift>bottom) return null // Yield rather than overlap the other island.
        capsule=capsule.shift(shift);touch=touch.shift(shift);handle=handle?.shift(shift)
    }
    val final=listOfNotNull(capsule,touch,handle)
    return IslandBounds(capsule,handle,touch,IslandRect(final.minOf{it.left},final.minOf{it.top},final.maxOf{it.right},final.maxOf{it.bottom}))
}

/** Bounded, in-memory deduplication. Reconnecting never replays the existing inbox. */
class IslandEvents {
    private val seen=linkedMapOf<String,Pair<String,Long>>()
    fun accept(key:String, fingerprint:String, posted:Long, now:Long):Boolean {
        if(posted>now+5_000 || now-posted>15_000) return false
        val previous=seen.put(key,fingerprint to posted)
        while(seen.size>128) seen.remove(seen.keys.first())
        return previous==null || previous.first!=fingerprint || posted-previous.second>30_000
    }
    fun clear() = seen.clear()
}
