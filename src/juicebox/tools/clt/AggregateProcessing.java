/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2011-2018 Broad Institute, Aiden Lab
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */

package juicebox.tools.clt;

import jargs.gnu.CmdLineParser;
import juicebox.tools.HiCTools;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Created for testing multiple CLTs at once
 * Basically scratch space
 */
class AggregateProcessing {


    public static void main(String[] argv) throws CmdLineParser.UnknownOptionException, CmdLineParser.IllegalOptionValueException {

        String hicFilePaths="/Users/nathanielmusial/CS_Projects/SMART_Projects/Testing_Files/HiC/gm12878_intra_nofrag_30.hic";//.Hic
        String PeaksFile="/Users/nathanielmusial/CS_Projects/SMART_Projects/Testing_Files/Other/GM12878_loop_list.txt";//.txt
        String SaveFolderPath="/Users/nathanielmusial/CS_Projects/SMART_Projects/Output";

        /*
        APAvsDistance test= new APAvsDistance();
        test.run();

        */




/*

        String hicpath = "/Users/muhammadsaadshamim/Desktop/Aiden Lab/local_hic/gm12878_intra_nofrag_30.hic";
        String respath = "/Users/muhammadsaadshamim/Desktop/result_hiccups";


        //writeMergedNoDupsFromTimeSeq(seqPath, newPath);

        String[] ll51231123 = {"hiccups", "--cpu",
                hicpath,respath};

                */
        String[] ll51231123 = {"compare", "0", "hg19",
                "https://hicfiles.s3.amazonaws.com/hiseq/gm12878/in-situ/combined_peaks_with_motifs.txt",
                //"/Users/muhammadsaadshamim/Desktop/MBR19_loops.txt",
                "/Users/muhammadsaadshamim/Desktop/result_hiccups/merged_loops.bedpe",
                "/Users/muhammadsaadshamim/Desktop/result_25kb_hiccups_compare.bedpe"};

        HiCTools.main(ll51231123);
//        started 9:20



        /*
        String[] ll51231123 = {"motifs",
                "hg19",
                "/Users/muhammadsaadshamim/Desktop/test_motifs/gm12878_2",
                "/Users/muhammadsaadshamim/Desktop/test_motifs/loops_clean.txt"};


        HiCTools.main(ll51231123);
*/

    }

    private static void writeMergedNoDupsFromTimeSeq(String seqPath, String newPath) {
        List<Integer[]> listPositions = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(seqPath))) {
            for (String line; (line = br.readLine()) != null; ) {
                String[] parts = line.split(",");
                listPositions.add(new Integer[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])});
            }
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }


        try {
            PrintWriter p0 = new PrintWriter(new FileWriter(newPath));
            for (int i = 0; i < listPositions.size(); i++) {
                Integer[] pos_xy_1 = listPositions.get(i);
                for (int j = i; j < listPositions.size(); j++) {
                    Integer[] pos_xy_2 = listPositions.get(j);
                    double value = 1. / Math.max(1, Math.sqrt((pos_xy_1[0] - pos_xy_2[0]) ^ 2 + (pos_xy_1[1] - pos_xy_2[1]) ^ 2));
                    float conv_val = (float) value;
                    if (!Float.isNaN(conv_val) && conv_val > 0) {
                        p0.println("0 art " + i + " 0 16 art " + j + " 1 " + conv_val);
                    }
                }
            }
            p0.close();
        } catch (IOException ignored) {
            ignored.printStackTrace();
        }
    }
}