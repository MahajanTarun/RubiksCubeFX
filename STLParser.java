import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class is a parser for STL files. Currently, normals specified in the 
 * file are ignored and recalculated under the assumption that the coordinates 
 * are provided in right-handed coordinate space (counter-clockwise).
 * @author CCHall
 */
public class STLParser {
	/**
	 * Parses an STL file, attempting to automatically detect whether the file 
	 * is an ASCII or binary STL file
	 * @param filepath The file to parse
	 * @return A list of triangles representing all of the triangles in the STL 
	 * file.
	 * @throws IOException Thrown if there was a problem reading the file 
	 * (typically means the file does not exist or is not a file).
	 * @throws IllegalArgumentException Thrown if the STL is not properly 
	 * formatted
	 */
	public static List<Triangle> parseSTLFile(Path filepath) throws IOException{
		byte[] allBytes = Files.readAllBytes(filepath);
		// determine if it is ASCII or binary STL
		Charset charset = Charset.forName("UTF-8");
		CharBuffer decode = charset.decode(ByteBuffer.wrap(allBytes, 0, 80));
		String headerString = decode.toString();
		int index = 0; 
		while(Character.isWhitespace(headerString.charAt(index)) && index < 80){
			index++;
		}
		String firstWord = headerString.substring(index);
		boolean isASCIISTL = (firstWord.toLowerCase().startsWith("solid"));

		// read file to array of triangles
		List<Triangle> mesh;
		if(isASCIISTL){
			mesh = readASCII(charset.decode(ByteBuffer.wrap(allBytes)).toString().toLowerCase());
		} else {
			mesh = readBinary(allBytes);
		}
		return mesh;
	}
	/**
	 * Reads an STL ASCII file content provided as a String
	 * @param content ASCII STL
	 * @return A list of triangles representing all of the triangles in the STL 
	 * file.
	 * @throws IllegalArgumentException Thrown if the STL is not properly 
	 * formatted
	 */
	public static List<Triangle> readASCII(String content) {
		Logger.getLogger(STLParser.class.getName()).log(Level.FINEST,"Parsing ASCII STL format");
		// string is lowercase
		ArrayList<Triangle> triangles = new ArrayList<>();
		
		int position = 0;
		scan:
		{
			while (position < content.length() & position >= 0) {
				position = content.indexOf("facet", position);
				if (position < 0) {
					break scan;
				}
				try {
					Vec3d[] vertices = new Vec3d[3];
					for (int v = 0; v < vertices.length; v++) {
						position = content.indexOf("vertex", position) + "vertex".length();
						while (Character.isWhitespace(content.charAt(position))) {
							position++;
						}
						int nextSpace;
						double[] vals = new double[3];
						for (int d = 0; d < vals.length; d++) {
							nextSpace = position + 1;
							while (!Character.isWhitespace(content.charAt(nextSpace))) {
								nextSpace++;
							}
							String value = content.substring(position, nextSpace);
							vals[d] = Double.parseDouble(value);
							position = nextSpace;
							while (Character.isWhitespace(content.charAt(position))) {
								position++;
							}
						}
						vertices[v] = new Vec3d(vals[0], vals[1], vals[2]);
					}
					position = content.indexOf("endfacet", position)+"endfacet".length();
					triangles.add(new Triangle(vertices[0], vertices[1], vertices[2]));
				} catch (Exception ex) {
					int back = position - 128;
					if (back < 0) {
						back = 0;
					}
					int forward = position + 128;
					if (position > content.length()) {
						forward = content.length();
					}
					throw new IllegalArgumentException("Malformed STL syntax near \"" + content.substring(back, forward) + "\"", ex);
				}
			}
		}
		
		return triangles;
	}
	
	
	/**
	 * Parses binary STL file content provided as a byte array
	 * @param allBytes binary STL
	 * @return A list of triangles representing all of the triangles in the STL 
	 * file.
	 * @throws IllegalArgumentException Thrown if the STL is not properly 
	 * formatted
	 */
	public static List<Triangle> readBinary(byte[] allBytes) {
		Logger.getLogger(STLParser.class.getName()).log(Level.FINEST,"Parsing binary STL format");
		DataInputStream in = new DataInputStream(new ByteArrayInputStream(allBytes));
		ArrayList<Triangle> triangles = new ArrayList<>();
		try{
			// skip the header
			byte[] header = new byte[80];
			in.read(header);
			// get number triangles (not really needed)
			// WARNING: STL FILES ARE SMALL-ENDIAN
			int numberTriangles = Integer.reverseBytes(in.readInt());
			triangles.ensureCapacity(numberTriangles);
			// read triangles
			try{
				while(in.available() > 0 ){
					float[] nvec = new float[3];
					for(int i = 0; i < nvec.length; i++){
						nvec[i] = Float.intBitsToFloat(Integer.reverseBytes(in.readInt()));
					}
					Vec3d normal = new Vec3d(nvec[0],nvec[1],nvec[2]); // not used (yet)
					Vec3d[] vertices = new Vec3d[3];
					for (int v = 0; v < vertices.length; v++) {
						float[] vals = new float[3];
						for (int d = 0; d < vals.length; d++) {
							vals[d] = Float.intBitsToFloat(Integer.reverseBytes(in.readInt()));
						}
						vertices[v] = new Vec3d(vals[0], vals[1], vals[2]);
					}
					short attribute = Short.reverseBytes(in.readShort()); // not used (yet)
					triangles.add(new Triangle(vertices[0], vertices[1], vertices[2]));
				}
			}catch(Exception ex){
				throw new IllegalArgumentException("Malformed STL binary at triangle number " + (triangles.size()+1), ex);
			}
		}catch(IOException ex){
			// IO exceptions are impossible with byte array input streams, 
			// but still need to be caught
			Logger.getLogger(STLParser.class.getName()).log(Level.SEVERE, "HOLY SHIT! A ByteArrayInputStream threw an exception!", ex);
		}
		return triangles;
	}
	
}