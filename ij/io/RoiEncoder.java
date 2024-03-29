package ij.io;
import ij.gui.*;
import ij.process.FloatPolygon;
import java.awt.*;
import java.io.*;
import java.util.*;
import java.net.*;
import java.awt.geom.*;


/** Saves an ROI to a file or stream. RoiDecoder.java has a description of the file format.
	@see ij.io.RoiDecoder
	@see ij.plugin.RoiReader
*/
public class RoiEncoder {
	static final int HEADER_SIZE = 64;
	static final int HEADER2_SIZE = 64;
	static final int VERSION = 224; // changed to 224 : roi properties
	private String path;
	private OutputStream f;
	private final int polygon=0, rect=1, oval=2, line=3, freeline=4, polyline=5, noRoi=6, freehand=7, 
		traced=8, angle=9, point=10;
	private byte[] data;
	private String roiName;
	private int roiNameSize;
	private String roiProps;
	private int roiPropsSize;
	
	/** Creates an RoiEncoder using the specified path. */
	public RoiEncoder(String path) {
		this.path = path;
	}

	/** Creates an RoiEncoder using the specified OutputStream. */
	public RoiEncoder(OutputStream f) {
		this.f = f;
	}

	/** Save the Roi to the file of stream. */
	public void write(Roi roi) throws IOException {
		if (f!=null) {
			write(roi, f);
		} else {
			f = new FileOutputStream(path);
			write(roi, f);
			f.close();
		}
	}
	
	/** Saves the specified ROI as a byte array. */
	public static byte[] saveAsByteArray(Roi roi) {
		if (roi==null) return null;
		byte[] bytes = null;
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
			RoiEncoder encoder = new RoiEncoder(out);
			encoder.write(roi);
			out.close();
			bytes = out.toByteArray(); 
		} catch (IOException e) {
			return null;
		}
		return bytes;
	}

	void write(Roi roi, OutputStream f) throws IOException {
		int roiType = roi.getType();
		int type = rect;
		int options = 0;
		roiName = roi.getName();
		if (roiName!=null)
			roiNameSize = roiName.length()*2;
		else
			roiNameSize = 0;
		
		roiProps = roi.getProperties();
		if (roiProps!=null)
			roiPropsSize = roiProps.length()*2;
		else
			roiPropsSize = 0;

		switch (roiType) {
			case Roi.POLYGON: type=polygon; break;
			case Roi.FREEROI: type=freehand; break;
			case Roi.TRACED_ROI: type=traced; break;
			case Roi.OVAL: type=oval; break;
			case Roi.LINE: type=line; break;
			case Roi.POLYLINE: type=polyline; break;
			case Roi.FREELINE: type=freeline; break;
			case Roi.ANGLE: type=angle; break;
			case Roi.COMPOSITE: type=rect; break; // shape array size (36-39) will be >0 to indicate composite type
			case Roi.POINT: type=point; break;
			default: type = rect; break;
		}
		
		if (roiType==Roi.COMPOSITE) {
			saveShapeRoi(roi, type, f, options);
			return;
		}

		int n=0;
		int[] x=null, y=null;
		float[] xf=null, yf=null;
		int floatSize = 0;
		Rectangle r = roi.getBounds();
		if (roi instanceof PolygonRoi) {
			PolygonRoi proi = (PolygonRoi)roi;
			Polygon p = proi.getNonSplineCoordinates();
			n = p.npoints;
			x = p.xpoints;
			y = p.ypoints;
			if (roi.subPixelResolution()) {
				FloatPolygon fp = null;
				if (proi.isSplineFit())
					fp = proi.getNonSplineFloatPolygon();
				else
					fp = roi.getFloatPolygon();
				if (n==fp.npoints) {
					options |= RoiDecoder.SUB_PIXEL_RESOLUTION;
					if (roi.getDrawOffset())
						options |= RoiDecoder.DRAW_OFFSET;
					xf = fp.xpoints;
					yf = fp.ypoints;
					floatSize = n*8;
				}
			}
		}
		
		data = new byte[HEADER_SIZE+HEADER2_SIZE+n*4+floatSize+roiNameSize+roiPropsSize];
		data[0]=73; data[1]=111; data[2]=117; data[3]=116; // "Iout"
		putShort(RoiDecoder.VERSION_OFFSET, VERSION);
		data[RoiDecoder.TYPE] = (byte)type;
		putShort(RoiDecoder.TOP, r.y);
		putShort(RoiDecoder.LEFT, r.x);
		putShort(RoiDecoder.BOTTOM, r.y+r.height);
		putShort(RoiDecoder.RIGHT, r.x+r.width);	
		if (roi.subPixelResolution() && (type==rect||type==oval)) {
			FloatPolygon p = roi.getFloatPolygon();
			if (p.npoints==4) {
				putFloat(RoiDecoder.XD, p.xpoints[0]);
				putFloat(RoiDecoder.YD, p.ypoints[0]);
				putFloat(RoiDecoder.WIDTHD, p.xpoints[1]-p.xpoints[0]);
				putFloat(RoiDecoder.HEIGHTD, p.ypoints[2]-p.ypoints[1]);	
				options |= RoiDecoder.SUB_PIXEL_RESOLUTION;
				putShort(RoiDecoder.OPTIONS, options);
			}
		}
		putShort(RoiDecoder.N_COORDINATES, n);
		putInt(RoiDecoder.POSITION, roi.getPosition());
		
		if (type==rect) {
			int arcSize = roi.getCornerDiameter();
			if (arcSize>0)
				putShort(RoiDecoder.ROUNDED_RECT_ARC_SIZE, arcSize);
		}
		
		if (roi instanceof Line) {
			Line line = (Line)roi;
			putFloat(RoiDecoder.X1, (float)line.x1d);
			putFloat(RoiDecoder.Y1, (float)line.y1d);
			putFloat(RoiDecoder.X2, (float)line.x2d);
			putFloat(RoiDecoder.Y2, (float)line.y2d);
			if (roi instanceof Arrow) {
				putShort(RoiDecoder.SUBTYPE, RoiDecoder.ARROW);
				if (((Arrow)roi).getDoubleHeaded())
					options |= RoiDecoder.DOUBLE_HEADED;
				if (((Arrow)roi).getOutline())
					options |= RoiDecoder.OUTLINE;
				putShort(RoiDecoder.OPTIONS, options);
				putByte(RoiDecoder.ARROW_STYLE, ((Arrow)roi).getStyle());
				putByte(RoiDecoder.ARROW_HEAD_SIZE, (int)((Arrow)roi).getHeadSize());
			} else {
				if (roi.getDrawOffset())
					options |= RoiDecoder.SUB_PIXEL_RESOLUTION+RoiDecoder.DRAW_OFFSET;
			}
		}
		
		if (roi instanceof EllipseRoi) {
			putShort(RoiDecoder.SUBTYPE, RoiDecoder.ELLIPSE);
			double[] p = ((EllipseRoi)roi).getParams();
			putFloat(RoiDecoder.X1, (float)p[0]);
			putFloat(RoiDecoder.Y1, (float)p[1]);
			putFloat(RoiDecoder.X2, (float)p[2]);
			putFloat(RoiDecoder.Y2, (float)p[3]);
			putFloat(RoiDecoder.ELLIPSE_ASPECT_RATIO, (float)p[4]);
		}

		// save stroke width, stroke color and fill color (1.43i or later)
		if (VERSION>=218) {
			saveStrokeWidthAndColor(roi);
			if ((roi instanceof PolygonRoi) && ((PolygonRoi)roi).isSplineFit()) {
				options |= RoiDecoder.SPLINE_FIT;
				putShort(RoiDecoder.OPTIONS, options);
			}
		}
		
		// save TextRoi
		if (n==0 && roi instanceof TextRoi)
			saveTextRoi((TextRoi)roi);
		else if (n==0 && roi instanceof ImageRoi)
			saveImageRoi((ImageRoi)roi);
		else
			putHeader2(roi, HEADER_SIZE+n*4+floatSize);
			
		if (n>0) {
			int base1 = 64;
			int base2 = base1+2*n;
			for (int i=0; i<n; i++) {
				putShort(base1+i*2, x[i]);
				putShort(base2+i*2, y[i]);
			}
			if (xf!=null) {
				base1 = 64+4*n;
				base2 = base1+4*n;
				for (int i=0; i<n; i++) {
					putFloat(base1+i*4, xf[i]);
					putFloat(base2+i*4, yf[i]);
				}
			}
		}
		
		saveOverlayOptions(roi, options);
		f.write(data);
	}

	void saveStrokeWidthAndColor(Roi roi) {
		BasicStroke stroke = roi.getStroke();
		if (stroke!=null)
			putShort(RoiDecoder.STROKE_WIDTH, (int)stroke.getLineWidth());
		Color strokeColor = roi.getStrokeColor();
		if (strokeColor!=null)
			putInt(RoiDecoder.STROKE_COLOR, strokeColor.getRGB());
		Color fillColor = roi.getFillColor();
		if (fillColor!=null)
			putInt(RoiDecoder.FILL_COLOR, fillColor.getRGB());
	}

	void saveShapeRoi(Roi roi, int type, OutputStream f, int options) throws IOException {
		float[] shapeArray = ((ShapeRoi)roi).getShapeAsArray();
		if (shapeArray==null) return;
		BufferedOutputStream bout = new BufferedOutputStream(f);
		Rectangle r = roi.getBounds();
		data  = new byte[HEADER_SIZE+HEADER2_SIZE+shapeArray.length*4+roiNameSize];
		data[0]=73; data[1]=111; data[2]=117; data[3]=116; // "Iout"
		
		putShort(RoiDecoder.VERSION_OFFSET, VERSION);
		data[RoiDecoder.TYPE] = (byte)type;
		putShort(RoiDecoder.TOP, r.y);
		putShort(RoiDecoder.LEFT, r.x);
		putShort(RoiDecoder.BOTTOM, r.y+r.height);
		putShort(RoiDecoder.RIGHT, r.x+r.width);	
		putInt(RoiDecoder.POSITION, roi.getPosition());
		//putShort(16, n);
		putInt(36, shapeArray.length); // non-zero segment count indicate composite type
		if (VERSION>=218) saveStrokeWidthAndColor(roi);
		saveOverlayOptions(roi, options);

		// handle the actual data: data are stored segment-wise, i.e.,
		// the type of the segment followed by 0-6 control point coordinates.
		int base = 64;
		for (int i=0; i<shapeArray.length; i++) {
			putFloat(base, shapeArray[i]);
			base += 4;
		}
		int hdr2Offset = HEADER_SIZE+shapeArray.length*4;
		//ij.IJ.log("saveShapeRoi: "+HEADER_SIZE+"  "+shapeArray.length);
		putHeader2(roi, hdr2Offset);
		bout.write(data,0,data.length);
		bout.flush();
	}
	
	void saveOverlayOptions(Roi roi, int options) {
		Overlay proto = roi.getPrototypeOverlay();
		if (proto.getDrawLabels())
			options |= RoiDecoder.OVERLAY_LABELS;
		if (proto.getDrawNames())
			options |= RoiDecoder.OVERLAY_NAMES;
		if (proto.getDrawBackgrounds())
			options |= RoiDecoder.OVERLAY_BACKGROUNDS;
		Font font = proto.getLabelFont();
		if (font!=null && font.getStyle()==Font.BOLD)
			options |= RoiDecoder.OVERLAY_BOLD;
		putShort(RoiDecoder.OPTIONS, options);
	}
	
	void saveTextRoi(TextRoi roi) {
		Font font = roi.getCurrentFont();
		String fontName = font.getName();
		int size = font.getSize();
		int drawStringMode = roi.getDrawStringMode()?1024:0;
		int style = font.getStyle() + roi.getJustification()*256+drawStringMode;
		String text = roi.getText();
		int fontNameLength = fontName.length();
		int textLength = text.length();
		int textRoiDataLength = 16+fontNameLength*2+textLength*2;
		byte[] data2 = new byte[HEADER_SIZE+HEADER2_SIZE+textRoiDataLength+roiNameSize];
		System.arraycopy(data, 0, data2, 0, HEADER_SIZE);
		data = data2;
		putShort(RoiDecoder.SUBTYPE, RoiDecoder.TEXT);
		putInt(HEADER_SIZE, size);
		putInt(HEADER_SIZE+4, style);
		putInt(HEADER_SIZE+8, fontNameLength);
		putInt(HEADER_SIZE+12, textLength);
		for (int i=0; i<fontNameLength; i++)
			putShort(HEADER_SIZE+16+i*2, fontName.charAt(i));
		for (int i=0; i<textLength; i++)
			putShort(HEADER_SIZE+16+fontNameLength*2+i*2, text.charAt(i));
		int hdr2Offset = HEADER_SIZE+textRoiDataLength;
		//ij.IJ.log("saveTextRoi: "+HEADER_SIZE+"  "+textRoiDataLength+"  "+fontNameLength+"  "+textLength);
		putHeader2(roi, hdr2Offset);
	}
	
	void saveImageRoi(ImageRoi roi) {
		byte[] bytes = roi.getSerializedImage();
		int imageSize = bytes.length;
		byte[] data2 = new byte[HEADER_SIZE+HEADER2_SIZE+imageSize+roiNameSize];
		System.arraycopy(data, 0, data2, 0, HEADER_SIZE);
		data = data2;
		putShort(RoiDecoder.SUBTYPE, RoiDecoder.IMAGE);
		for (int i=0; i<imageSize; i++)
			putByte(HEADER_SIZE+i, bytes[i]&255);
		int hdr2Offset = HEADER_SIZE+imageSize;
		double opacity = roi.getOpacity();
		putByte(hdr2Offset+RoiDecoder.IMAGE_OPACITY, (int)(opacity*255.0));
		putInt(hdr2Offset+RoiDecoder.IMAGE_SIZE, imageSize);
		putHeader2(roi, hdr2Offset);
	}

	void putHeader2(Roi roi, int hdr2Offset) {
		//ij.IJ.log("putHeader2: "+hdr2Offset+" "+roiNameSize+"  "+roiName);
		putInt(RoiDecoder.HEADER2_OFFSET, hdr2Offset);
		putInt(hdr2Offset+RoiDecoder.C_POSITION, roi.getCPosition());
		putInt(hdr2Offset+RoiDecoder.Z_POSITION, roi.getZPosition());
		putInt(hdr2Offset+RoiDecoder.T_POSITION, roi.getTPosition());
		Overlay proto = roi.getPrototypeOverlay();
		Color overlayLabelColor = proto.getLabelColor();
		if (overlayLabelColor!=null)
			putInt(hdr2Offset+RoiDecoder.OVERLAY_LABEL_COLOR, overlayLabelColor.getRGB());
		Font font = proto.getLabelFont();
		if (font!=null)
			putShort(hdr2Offset+RoiDecoder.OVERLAY_FONT_SIZE, font.getSize());
		if (roiNameSize>0)
			putName(roi, hdr2Offset);
		double strokeWidth = roi.getStrokeWidth();
		if (roi.getStroke()==null)
			strokeWidth = 0.0;
		putFloat(hdr2Offset+RoiDecoder.FLOAT_STROKE_WIDTH, (float)strokeWidth);
		if (roiPropsSize>0)
			putProps(roi, hdr2Offset);

	}

	void putName(Roi roi, int hdr2Offset) {
		int offset = hdr2Offset+HEADER2_SIZE;
		int nameLength = roiNameSize/2;
		putInt(hdr2Offset+RoiDecoder.NAME_OFFSET, offset);
		putInt(hdr2Offset+RoiDecoder.NAME_LENGTH, nameLength);
		for (int i=0; i<nameLength; i++)
			putShort(offset+i*2, roiName.charAt(i));
	}

	void putProps(Roi roi, int hdr2Offset) {
		int offset = hdr2Offset+HEADER2_SIZE+roiNameSize;
		int roiPropsLength = roiPropsSize/2;
		putInt(hdr2Offset+RoiDecoder.ROI_PROPS_OFFSET, offset);
		putInt(hdr2Offset+RoiDecoder.ROI_PROPS_LENGTH, roiPropsLength);
		for (int i=0; i<roiPropsLength; i++)
			putShort(offset+i*2, roiProps.charAt(i));
	}

    void putByte(int base, int v) {
		data[base] = (byte)v;
    }

    void putShort(int base, int v) {
		data[base] = (byte)(v>>>8);
		data[base+1] = (byte)v;
    }

	void putFloat(int base, float v) {
		int tmp = Float.floatToIntBits(v);
		data[base]   = (byte)(tmp>>24);
		data[base+1] = (byte)(tmp>>16);
		data[base+2] = (byte)(tmp>>8);
		data[base+3] = (byte)tmp;
	}

	void putInt(int base, int i) {
		data[base]   = (byte)(i>>24);
		data[base+1] = (byte)(i>>16);
		data[base+2] = (byte)(i>>8);
		data[base+3] = (byte)i;
	}
	
	
}