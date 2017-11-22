package water.fvec;

import water.DKV;
import water.Key;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.parser.BufferedString;

/**
 * Methods to access frame internals allowing us to create testing frames.
 * This class is based on the the code in https://github.com/h2oai/h2o-3
 */
public class FrameTestCreator {

    public static Frame createFrame(String fname, long[] chunkLayout, String[][] data) {
        Frame f = new Frame(Key.<Frame>make(fname));
        f.preparePartialFrame(new String[]{"C0"});
        f.update();
        // Create chunks
        byte[] types = new byte[]{Vec.T_STR};
        for (int i = 0; i < chunkLayout.length; i++) {
            createNC(fname, data[i], i, (int) chunkLayout[i], types);
        }
        // Reload frame from DKV
        f = DKV.get(fname).get();
        // Finalize frame
        f.finalizePartialFrame(chunkLayout, new String[][]{null}, types);
        return f;
    }

    public static NewChunk createNC(String fname, String[] data, int cidx, int len, byte[] types) {
        NewChunk[] nchunks = Frame.createNewChunks(fname, types, cidx);
        for (int i = 0; i < len; i++) {
            nchunks[0].addStr(data[i] != null ? data[i] : null);
        }
        Frame.closeNewChunks(nchunks);
        return nchunks[0];
    }

    public static Frame createFrame(String fname, long[] chunkLayout) {
        // Create a frame
        Frame f = new Frame(Key.<Frame>make(fname));
        f.preparePartialFrame(new String[]{"C0"});
        f.update();
        byte[] types = new byte[]{Vec.T_NUM};
        // Create chunks
        for (int i = 0; i < chunkLayout.length; i++) {
            createNC(fname, i, (int) chunkLayout[i], types);
        }
        // Reload frame from DKV
        f = DKV.get(fname).get();
        // Finalize frame
        f.finalizePartialFrame(chunkLayout, new String[][]{null}, types);
        return f;
    }

    public static NewChunk createNC(String fname, int cidx, int len, byte[] types) {
        NewChunk[] nchunks = Frame.createNewChunks(fname, types, cidx);
        int starVal = cidx * 1000;
        for (int i = 0; i < len; i++) {
            nchunks[0].addNum(starVal + i);
        }
        Frame.closeNewChunks(nchunks);
        return nchunks[0];
    }

    public static String[] collectS(Vec v) {
        String[] res = new String[(int) v.length()];
        BufferedString tmpStr = new BufferedString();
        for (int i = 0; i < v.length(); i++)
            res[i] = v.isNA(i) ? null : v.atStr(tmpStr, i).toString();
        return res;
    }
}