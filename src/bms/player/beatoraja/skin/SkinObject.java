package bms.player.beatoraja.skin;

import bms.player.beatoraja.MainController;
import bms.player.beatoraja.MainState;
import bms.player.beatoraja.skin.Skin.SkinObjectRenderer;

import com.badlogic.gdx.graphics.Color;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Disposable;

import java.util.*;

import com.badlogic.gdx.utils.IntSet;

/**
 * スキンオブジェクト
 * 
 * @author exch
 */
public abstract class SkinObject implements Disposable {

	/**
	 * オフセットの参照ID
	 */
	private int[] offset = new int[0];

	private boolean relative;

	private int imageid = -1;
	/**
	 * 参照するタイマーID
	 */
	private int dsttimer = 0;
	/**
	 * ループ開始タイマー
	 */
	private int dstloop = 0;
	/**
	 * ブレンド(2:加算, 9:反転)
	 */
	private int dstblend = 0;
    /**
     * 0 : Nearest neighbor
     * 1 : Linear filtering
     */
	private int dstfilter;
	
	private int imageType;
	
	/**
	 * 画像回転の中心
	 */
	private int dstcenter;

	private int acc;
	/**
	 * オブジェクトクリック時に実行するイベントの参照ID
	 */
	private int clickevent = -1;
	/**
	 * 描画条件となるオプション定義
	 */
	private int[] dstop = new int[0];

	private final float[] CENTERX = { 0.5f, 0, 0.5f, 1, 0, 0.5f, 1, 0, 0.5f, 1, };
	private final float[] CENTERY = { 0.5f, 0, 0, 0, 0.5f, 0.5f, 0.5f, 1, 1, 1 };

	private float centerx;
	private float centery;

	private SkinObjectDestination[] dst = new SkinObjectDestination[0];
	
	// 以下、高速化用
	private long starttime;
	private long endtime;

	private Rectangle r = new Rectangle();
	private Color c = new Color();
	private SkinOffset[] off = new SkinOffset[0];

	private Rectangle fixr = null;
	private Color fixc = null;
	private int fixa = Integer.MIN_VALUE;

	private long nowtime = 0;
	private float rate = 0;
	private int index = 0;
	
	public SkinObjectDestination[] getAllDestination() {
		return dst;
	}

	public void setDestination(long time, float x, float y, float w, float h, int acc, int a, int r, int g, int b,
			int blend, int filter, int angle, int center, int loop, int timer, int op1, int op2, int op3, int offset) {
		setDestination(time, x, y, w, h, acc, a, r, g, b, blend, filter, angle, center, loop, timer, new int[]{op1,op2,op3});
		setOffsetID(offset);
	}

	public void setDestination(long time, float x, float y, float w, float h, int acc, int a, int r, int g, int b,
							   int blend, int filter, int angle, int center, int loop, int timer, int op1, int op2, int op3, int[] offset) {
		setDestination(time, x, y, w, h, acc, a, r, g, b, blend, filter, angle, center, loop, timer, new int[]{op1,op2,op3});
		setOffsetID(offset);
	}

	public void setDestination(long time, float x, float y, float w, float h, int acc, int a, int r, int g, int b,
			int blend, int filter, int angle, int center, int loop, int timer, int[] op) {
		SkinObjectDestination obj = new SkinObjectDestination(time, new Rectangle(x, y, w, h), new Color(r / 255.0f,
				g / 255.0f, b / 255.0f, a / 255.0f), angle, acc);
		if (dst.length == 0) {
			fixr = obj.region;
			fixc = obj.color;
			fixa = obj.angle;
		} else {
			if (!obj.region.equals(fixr)) {
				fixr = null;
			}
			if (!obj.color.equals(fixc)) {
				fixc = null;
			}
			if (!(fixa == obj.angle)) {
				fixa = Integer.MIN_VALUE;
			}
		}
		if (this.acc == 0) {
			this.acc = acc;
		}
		if (dstblend == 0) {
			dstblend = blend;
		}
		
		if (dstfilter == 0) {
			dstfilter = filter;
		}
		
		if (dstcenter == 0 && center < 10) {
			dstcenter = center;
			centerx = CENTERX[center];
			centery = CENTERY[center];
		}
		if (dsttimer == 0) {
			dsttimer = timer;
		}
		if (dstloop == 0) {
			dstloop = loop;
		}
		if (dstop.length == 0) {
			List<Integer> l = new ArrayList<Integer>();
			for(int i : op) {
				if(i != 0) {
					l.add(i);
				}
			}
			op = new int[l.size()];
			for (int i = 0; i < l.size(); i++) {
				op[i] = l.get(i);
			}
			dstop = op;
		}
		for (int i = 0; i < dst.length; i++) {
			if (dst[i].time > time) {
				List<SkinObjectDestination> l = new ArrayList<SkinObjectDestination>(Arrays.asList(dst));
				l.add(i, obj);
				dst = l.toArray(new SkinObjectDestination[l.size()]);
				starttime = dst[0].time;
				endtime = dst[dst.length - 1].time;
				return;
			}
		}
		List<SkinObjectDestination> l = new ArrayList<SkinObjectDestination>(Arrays.asList(dst));
		l.add(obj);
		dst = l.toArray(new SkinObjectDestination[l.size()]);
		starttime = dst[0].time;
		endtime = dst[dst.length - 1].time;
	}

	public int[] getOption() {
		return dstop;
	}

	public void setOption(int[] dstop) {
		this.dstop = dstop;
	}

	public Rectangle getDestination(long time) {
		return this.getDestination(time, null);
	}

	/**
	 * 指定して時間に応じた描画領域を返す
	 * 
	 * @param time
	 *            時間(ms)
	 * @return 描画領域
	 */
	public Rectangle getDestination(long time, MainState state) {
		final int timer = dsttimer;

		if (timer != 0 && timer < MainController.timerCount) {
			final long stime = state.getTimer()[timer];
			if (stime == Long.MIN_VALUE) {
				return null;
			}
			time -= stime;
		}

		final long lasttime = endtime;
		if( dstloop == -1) {
			if(time > endtime) {
				time = -1;
			}
		} else if (lasttime > 0 && time > dstloop) {
			if (lasttime == dstloop) {
				time = dstloop;
			} else {
				time = (time - dstloop) % (lasttime - dstloop) + dstloop;
			}
		}
		if (starttime > time) {
			return null;
		}
		nowtime = time;
		rate = -1;
		index = -1;
		for(int i = 0;i < off.length;i++) {
			off[i] = state != null ? state.getOffsetValue(offset[i]) : null;
		}

		if (fixr == null) {
			getRate();
			if(rate == 0) {
				r.set(dst[index].region);
			} else {
				final Rectangle r1 = dst[index].region;
				final Rectangle r2 = dst[index + 1].region;
				r.x = r1.x + (r2.x - r1.x) * rate;
				r.y = r1.y + (r2.y - r1.y) * rate;
				r.width = r1.width + (r2.width - r1.width) * rate;
				r.height = r1.height + (r2.height - r1.height) * rate;
			}

			for(SkinOffset off : this.off) {
				if (off != null) {
					if(!relative) {
						r.x += off.x - off.w / 2;
						r.y += off.y - off.h / 2;
					}
					r.width += off.w;
					r.height += off.h;
				}
			}
			return r;
		} else {
			if (offset.length == 0) {
				return fixr;
			}
			r.set(fixr);
			for(SkinOffset off : this.off) {
				if (off != null) {
					if(!relative) {
						r.x += off.x - off.w / 2;
						r.y += off.y - off.h / 2;
					}
					r.width += off.w;
					r.height += off.h;
				}
			}
			return r;
		}
	}

	public Color getColor() {
		if (fixc != null) {
			c.set(fixc);
			for(SkinOffset off :this.off) {
				if(off != null) {
					float a = c.a + (off.a / 255.0f);
					a = a > 1 ? 1 : (a < 0 ? 0 : a);
					c.a = a;
				}
			}
			return c;
		}
		getRate();
		if(rate == 0) {
			c.set(dst[index].color);			
		} else {
			final Color r1 = dst[index].color;
			final Color r2 = dst[index + 1].color;
			c.r = r1.r + (r2.r - r1.r) * rate;
			c.g = r1.g + (r2.g - r1.g) * rate;
			c.b = r1.b + (r2.b - r1.b) * rate;
			c.a = r1.a + (r2.a - r1.a) * rate;
			return c;			
		}
		for(SkinOffset off :this.off) {
			if(off != null) {
				float a = c.a + (off.a / 255.0f);
				a = a > 1 ? 1 : (a < 0 ? 0 : a);
				c.a = a;
			}
		}
		return c;
	}

	public int getAngle() {
		if (fixa != Integer.MIN_VALUE) {
			int a = fixa;
			for(SkinOffset off :this.off) {
				if(off != null) {
					a += off.r;
				}
			}
			return a;
		}
		getRate();
		int a = (rate == 0 ? dst[index].angle :  (int) (dst[index].angle + (dst[index + 1].angle - dst[index].angle) * rate));
		for(SkinOffset off :this.off) {
			if(off != null) {
				a += off.r;
			}
		}
		return a;
	}
	
	private void getRate() {
		if(rate != -1) {
			return;
		}
		long time2 = dst[dst.length - 1].time;
		if(nowtime == time2) {
			this.rate = 0;
			this.index = dst.length - 1;
			return;
		}
		for (int i = dst.length - 2; i >= 0; i--) {
			final long time1 = dst[i].time;
			if (time1 <= nowtime && time2 > nowtime) {
				float rate = (float) (nowtime - time1) / (time2 - time1);
				switch(acc) {
				case 1:
					rate = rate * rate;
					break;
				case 2:
					rate = 1 - (rate - 1) * (rate - 1);
					break;
				}
				this.rate = rate;
				this.index = i;
				return;
			}
			time2 = time1;
		}
		this.rate = 0;
		this.index = 0;
	}

	public abstract void draw(SkinObjectRenderer sprite, long time, MainState state);

	protected void draw(SkinObjectRenderer sprite, TextureRegion image, float x, float y, float width, float height) {
		draw(sprite, image, x, y, width, height, getColor(), getAngle());
	}

	protected void draw(SkinObjectRenderer sprite, TextureRegion image, float x, float y, float width, float height,
			Color color, int angle) {
		if (color == null || color.a == 0f || image == null) {
			return;
		}
		sprite.setColor(color);
		sprite.setBlend(dstblend);
		sprite.setType(dstfilter != 0 && imageType == SkinObjectRenderer.TYPE_NORMAL ? SkinObjectRenderer.TYPE_BILINEAR : imageType);
		
		if (angle != 0) {
			sprite.draw(image, x, y, width, height, centerx , centery, angle);
		} else {
			sprite.draw(image, x, y, width, height);
		}
	}
	
	protected boolean mousePressed(MainState state, int button, int x, int y) {
		if (clickevent != -1) {
			Rectangle r = getDestination(state.getNowTime(), state);
			// System.out.println(obj.getClickevent() + " : " + r.x +
			// "," + r.y + "," + r.width + "," + r.height + " - " + x +
			// "," + y);
			if (r != null && r.x <= x && r.x + r.width >= x && r.y <= y && r.y + r.height >= y) {
				state.executeClickEvent(clickevent);
				return true;
			}
		}
		return false;
	}

	public int getClickevent() {
		return clickevent;
	}

	public void setClickevent(int clickevent) {
		this.clickevent = clickevent;
	}

	public boolean isRelative() {
		return relative;
	}

	public void setRelative(boolean relative) {
		this.relative = relative;
	}

	/**
	 * スキンオブジェクトの描画先を表現するクラス
	 * 
	 * @author exch
	 */
	public static class SkinObjectDestination {

		public final long time;
		/**
		 * 描画領域
		 */
		public final Rectangle region;
		public final int acc;
		public final Color color;
		public final int angle;

		public SkinObjectDestination(long time, Rectangle region, Color color, int angle, int acc) {
			this.time = time;
			this.region = region;
			this.color = color;
			this.angle = angle;
			this.acc = acc;
		}
	}
	
	public static class SkinOffset {
		public float x;
		public float y;
		public float w;
		public float h;
		public float r;
		public float a;
	}

	public abstract void dispose();

	public int[] getOffsetID() {
		return offset;
	}

	public void setOffsetID(int offset) {
		setOffsetID(new int[]{offset});
	}

	public void setOffsetID(int[] offset) {
		IntSet a = new IntSet(offset.length);
		for(int o : offset) {
			if(o > 0 && o < SkinProperty.OFFSET_MAX + 1) {
				a.add(o);
			}
		}
		if(a.size > 0) {
			this.offset = a.iterator().toArray().toArray();
			this.off = new SkinOffset[this.offset.length];
		}
	}

	public int getImageID() {
		return imageid;
	}

	public void setImageID(int imageid) {
		this.imageid = imageid;
	}

	public int getDestinationTimer() {
		return dsttimer;
	}
	
	public static void disposeAll(Disposable[] obj) {
		for(int i = 0;i < obj.length;i++) {
			if(obj[i] != null) {
				obj[i].dispose();
				obj[i] = null;
			}
		}
	}

	public int getImageType() {
		return imageType;
	}

	public void setImageType(int imageType) {
		this.imageType = imageType;
	}
	
	public int getFilter() {
		return dstfilter;
	}

	public void setFilter(int filter) {
		dstfilter = filter;
	}

}
