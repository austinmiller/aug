package aug.gui

import java.awt.{BorderLayout, Color, EventQueue, Font, Graphics}
import java.awt.event.{ComponentEvent, ComponentListener, MouseWheelEvent, MouseWheelListener}
import java.awt.image.BufferedImage
import javax.swing._

import aug.util.{LoremIpsum, Util}
import com.google.common.base.Splitter
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

import scala.collection.mutable.ArrayBuffer
import scala.util.Random

case class ColoredText(text: String, color: Color = Color.WHITE, background: Option[Color] = None)

class TextLine(texts: ArrayBuffer[ColoredText]) {
  import TextPanel._

  def this(s: String) {
    this(ArrayBuffer(ColoredText(s)))
  }

  val length : Int = texts map {_.text.length} sum
  val bf = new BufferedImage(fontWidth*length,fontHeight,BufferedImage.TYPE_INT_ARGB)
  var next : TextLine = null
  var prev : TextLine = null
  private val bfg = bf.createGraphics()
  bfg.setFont(TextPanel.font)

  {
    var pos = 0
    texts foreach { ct =>
      bfg.setColor(ct.color)
      bfg.drawString(ct.text, pos*fontWidth, fontHeight)
      pos += ct.text.length
    }
  }


  def height(): Int = {
    bf.getHeight()
  }

}


object TextPanel {
  val log = Logger(LoggerFactory.getLogger(TextPanel.getClass))

  val font = new Font( "Monospaced", Font.PLAIN, 16 )

  val (fontWidth,fontHeight) = {
    val bf = new BufferedImage(200,80,BufferedImage.TYPE_INT_RGB)
    val bfg = bf.createGraphics
    val metrics = bfg.getFontMetrics(font)
    (metrics.stringWidth("a"),metrics.getHeight)
  }

}

class TextPanel extends JPanel {

  import TextPanel._

  setBackground(Color.BLACK)

  @volatile
  var top : TextLine = new TextLine(Util.fullName)
  var scrollBot = top
  var bot = top

  var lines = 0
  var maxLines = 1000

  var texts = ArrayBuffer[ColoredText]()

  private var color = Color.WHITE

  private def addLine(tl: TextLine) = {
    tl.next = top
    top.prev = tl
    top = tl
    scrollBot = top

    if(lines == maxLines) {
      bot = bot.prev
      bot.next = null
    } else {
      lines += 1
    }
  }

  def addSystemLine(txt: String, color: Option[Color] = None) = synchronized {
    val c = color getOrElse Color.YELLOW
    addLine(new TextLine(ArrayBuffer[ColoredText](ColoredText(txt,c))))
  }

  def addText(txt: String) : Unit = synchronized {
    import scala.collection.JavaConversions._
    val lines = Splitter.on("\n").split(txt).toArray
    if(lines.length > 1) {
      lines.take(lines.size - 1).foreach { l =>
        texts += ColoredText(l,color)
        addLine(new TextLine(texts))
        texts = ArrayBuffer[ColoredText]()
      }
    }

    if(lines.last.length > 0) texts += ColoredText(lines.last,color)

    this.repaint()
  }


  def setCurrentColor(color: Color) = synchronized {
    this.color = color
  }

  def clear : Unit = synchronized {
    top = new TextLine("")
    texts = ArrayBuffer[ColoredText]()
    lines = 0
  }

  def scrollUp(lines: Int) : Unit = {
    for(i <- 0 to lines) {
      if(scrollBot.next != null) scrollBot = scrollBot.next
    }
  }

  def scrollDown(lines: Int) : Unit = {
    for(i <- 0 to lines) {
      if(scrollBot.prev != null) scrollBot = scrollBot.prev
    }
  }

  override def paint(g: Graphics): Unit = {
    super.paint(g)

    val t = System.currentTimeMillis

    var pos = g.getClipBounds.height - 5

    var cur = scrollBot


    if(texts.size > 0) {
      val tl = new TextLine(texts)
      pos -= tl.height
      g.drawImage(tl.bf, 5, pos, null)
    }

    while(cur != null &&  pos > 0) {
      pos -= cur.height
      g.drawImage(cur.bf, 5, pos, null)
      cur = cur.next
    }
    val rt = System.currentTimeMillis() - t

    if(rt>500) {
      log.debug(s"long render time $rt")
    }
  }

  def resize = {}

}

class SplitTextPanel extends JSplitPane with MouseWheelListener {
  private val topPanel = new TextPanel
  private val textPanel = new TextPanel

  private val topScrollPane = new JScrollPane(topPanel)
  private val scrollPane = new JScrollPane(textPanel)

  private val topScrollBar = topScrollPane.getVerticalScrollBar

  topScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED)
  topScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS)

  scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED)
  scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER)

  topPanel.setBackground(new Color(15,15,15))
  setOrientation(JSplitPane.VERTICAL_SPLIT)
  setDividerSize(1)
  setTopComponent(topScrollPane)
  setBottomComponent(scrollPane)
  textPanel.setVisible(true)

  addMouseWheelListener(this)
  SplitTextPanel.addMouseWheelListener(this)

  unsplit

  def unsplit(): Unit = {
    setDividerSize(0)
    setDividerLocation(0)
    topScrollPane.setVisible(false)
  }

  def isSplit = topScrollPane.isVisible

  def split : Unit = {
    topPanel.top = textPanel.top
    topScrollBar.setValue(topScrollBar.getMaximum)
    topScrollPane.setVisible(true)
    setDividerLocation(0.7)
    setDividerSize(4)
  }

  def handleDown(notches: Int) : Unit = {
    if(!isSplit) return
    val rect = topScrollBar.getVisibleRect
    topScrollBar.setValue(topScrollBar.getValue + SplitTextPanel.NOTCHES)
    if(rect.getHeight.toInt + topScrollBar.getValue >= topScrollBar.getMaximum) unsplit
  }

  def handleUp(notches: Int) : Unit = {
    if(!isSplit) split else topScrollBar.setValue(topScrollBar.getValue - SplitTextPanel.NOTCHES)
  }

  override def getDividerLocation(): Int = getParent.getWidth / 2
  override def getLastDividerLocation(): Int = getDividerLocation
  def resize : Unit = setDividerLocation(getDividerLocation)

  override def mouseWheelMoved(e: MouseWheelEvent): Unit = {
    val notches = e.getWheelRotation
    printf(s"mw moved $notches")
    if(notches < 0) handleUp(notches) else handleDown(notches)
    e.consume()
  }

  def addText(txt: String) : Unit = textPanel.addText(txt)
  def setCurrentColor(color: Color) = textPanel.setCurrentColor(color)
}

object SplitTextPanel extends JFrame with ComponentListener {

  val NOTCHES = 25
  val spt = new SplitTextPanel

  def setup = {
    setBackground(Color.RED)

    add(spt, BorderLayout.CENTER)
    setTitle("title")
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE)
    setVisible(true)
    setSize(800,600)
    spt.repaint()
  }

  def write(text: String, color: Color = Color.WHITE, wait: Long = 0) = {
    spt.setCurrentColor(color)
    spt.addText(text)
    Thread.sleep(wait)
  }

  def main(args: Array[String]): Unit = {
    EventQueue.invokeLater(new Runnable() { def run = SplitTextPanel.setup})

    val colors = List(Color.BLUE,Color.CYAN,Color.RED,Color.WHITE,Color.DARK_GRAY,Color.MAGENTA,Color.YELLOW,Color.PINK,Color.GREEN)

    val rand = new Random()

    def rcolor = colors(rand.nextInt(colors.size))

    val speed = 20

    for(i <- 0 to 10000) {
      if(rand.nextBoolean() == true) {
        for(j <- 0 to 6) {
          write(LoremIpsum.words(1) + " ",rcolor,rand.nextInt(speed/6))
        }
      } else {
        write(LoremIpsum.sentence, rcolor, rand.nextInt(speed))
      }
      write(LoremIpsum.sentence + LoremIpsum.sentence,rcolor)
      write("\n")
    }
  }

  override def componentShown(e: ComponentEvent): Unit = {}

  override def componentHidden(e: ComponentEvent): Unit = {}

  override def componentMoved(e: ComponentEvent): Unit = {}

  override def componentResized(e: ComponentEvent): Unit = { spt.resize }
}
