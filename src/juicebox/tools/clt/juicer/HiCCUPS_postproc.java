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

package juicebox.tools.clt.juicer;

import com.google.common.primitives.Doubles;
import com.google.common.primitives.Floats;
import jcuda.runtime.JCuda;
import juicebox.HiCGlobals;
import juicebox.data.*;
import juicebox.mapcolorui.Feature2DHandler;
import juicebox.tools.clt.CommandLineParserForJuicer;
import juicebox.tools.clt.JuicerCLT;
import juicebox.tools.utils.common.ArrayTools;
import juicebox.tools.utils.juicer.hiccups.GPUController;
import juicebox.tools.utils.juicer.hiccups.GPUOutputContainer;
import juicebox.tools.utils.juicer.hiccups.HiCCUPSConfiguration;
import juicebox.tools.utils.juicer.hiccups.HiCCUPSUtils;
import juicebox.track.feature.Feature2D;
import juicebox.track.feature.Feature2DList;
import juicebox.track.feature.Feature2DParser;
import juicebox.track.feature.Feature2DTools;
import juicebox.windowui.HiCZoom;
import juicebox.windowui.NormalizationType;
import org.broad.igv.feature.Chromosome;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HiC Computational Unbiased Peak Search
 * <p/>
 * Developed by Suhas Rao
 * Implemented by Muhammad Shamim
 * <p/>
 * -------
 * HiCCUPS
 * -------
 * <p/>
 * hiccups [-m matrixSize] [-c chromosome(s)] [-r resolution(s)] [-f fdr] [-p peak width] [-w window]
 * [-t thresholds] [-d centroid distances] <hicFile(s)> <finalLoopsList>
 * <p/>
 * hiccups [-m matrixSize] [-c chromosome(s)] [-r resolution(s)] [-f fdr] [-p peak width] [-i window]
 * <hicFile(s)> <fdrThresholds> <enrichedPixelsList>
 * <p/>
 * The required arguments are:
 * <p/>
 * <hic file(s)>: Address of HiC File(s) which should end with .hic.  This is the file you will
 * load into Juicebox. URLs or local addresses may be used. To sum multiple HiC Files together,
 * use the '+' symbol between the addresses (no whitespace between addresses).
 * <p/>
 * <finalLoopsList>: Final list of all loops found by HiCCUPS. Can be visualized directly in Juicebox as a 2D annotation.
 * By default, various values critical to the HICCUPS algorithm are saved as attributes for each loop found. These can be
 * disabled using the suppress flag below.
 * <p/>
 * -- OR -- If you do not want to run post processing and simply want the enriched pixels
 * <p/>
 * <fdrThresholds>: thresholds used in the HiCCUPS algorithm for the Bottom Left, Donut, Horizontal, and Vertical masks
 * <p/>
 * <enrichedPixelsList>: Final list of all enriched pixels found by HiCCUPS. Can be visualized directly in Juicebox as
 * 2D annotations.
 * <p/>
 * <p/>
 * The optional arguments are:
 * <p/>
 * -m <int> Maximum size of the submatrix within the chromosome passed on to GPU (Must be an even number greater than 40
 * to prevent issues from running the CUDA kernel). The upper limit will depend on your GPU. Dedicated GPUs
 * should be able to use values such as 500, 1000, or 2048 without trouble. Integrated GPUs are unlikely to run
 * sizes larger than 90 or 100. Matrix size will not effect the result, merely the time it takes for hiccups.
 * Larger values (with a dedicated GPU) will run fastest.
 * <p/>
 * -c <String(s)> Chromosome(s) on which HiCCUPS will be run. The number/letter for the chromosome can be used with or
 * without appending the "chr" string. Multiple chromosomes can be specified using commas (e.g. 1,chr2,X,chrY)
 * <p/>
 * -r <int(s)> Resolution(s) for which HiCCUPS will be run. Multiple resolutions can be specified using commas
 * (e.g. 25000,10000,5000). Due ot the nature of DNA looping, it is unlikely that loops will be found at
 * lower resolutions (i.e. 50kB or 100kB)
 * IMPORTANT: if multiple resolutions are used, the flags below can be configured so that different parameters are
 * used for the different resolutions.
 * <p/>
 * -f <int(s)> FDR values actually corresponding to max_q_val (i.e. for 1% FDR use 0.01, for 10%FDR use 0.1). Different
 * FDR values can be used for each resolution using commas. (e.g "-r 5000,10000 -f 0.1,0.15" would run HiCCUPS at
 * 10% FDR for resolution 5000 and 15% FDR for resolution 10000)
 * <p/>
 * -p <int(s)> Peak width used for finding enriched pixels in HiCCUPS. Different peak widths can be used for each
 * resolution using commas. (e.g "-r 5000,10000 -p 4,2" would run at peak width 4 for resolution 5000 and
 * peak width 2 for resolution 10000)
 * <p/>
 * -w <int(s)> Window width used for finding enriched pixels in HiCCUPS. Different window widths can be used for each
 * resolution using commas. (e.g "-r 5000,10000 -p 10,6" would run at window width 10 for resolution 5000 and
 * window width 6 for resolution 10000)
 * <p/>
 * -t <floats> Thresholds for merging loop lists of different resolutions. Four values must be given, separated by
 * commas (e.g. 0.02,1.5,1.75,2). These thresholds (in order) represent:
 * > threshold allowed for sum of FDR values of horizontal, vertical donut mask, and bottom left regions
 * (an accepted loop must stay below this threshold)
 * > threshold ratio of observed value to expected horizontal/vertical value
 * (an accepted loop must exceed this threshold)
 * > threshold ratio of observed value to expected donut mask value
 * (an accepted loop must exceed this threshold)
 * > threshold ratio of observed value to expected bottom left value
 * (an accepted loop must exceed this threshold)
 * <p/>
 * -d <ints> Distances used for merging centroids across different resolutions. Three values must be given, separated by
 * commas (e.g. 20000,20000,50000). These thresholds (in order) represent:
 * > distance (radius) around centroid used for merging at 5kB resolution (if present)
 * > distance (radius) around centroid used for merging at 10kB resolution (if present)
 * > distance (radius) around centroid used for merging at 25kB resolution (if present)
 * If a resolution (5kB, 10kB, or 25kB) is not available, that centroid distance will be ignored during the merger
 * step (but a distance value should still be passed as a parameter for that resolution e.g. 0)
 * <p/>
 * ----------------
 * HiCCUPS Examples
 * ----------------
 * <p/>
 * hiccups HIC006.hic all_hiccups_loops
 * > This command will run HiCCUPS on HIC006 and save all found loops to the all_hiccups_loops files
 * <p/>
 * hiccups -m 500 -r 5000,10000 -f 0.1,0.1 -p 4,2 -w 7,5 -d 20000,20000,0  -c 22  HIC006.hic all_hiccups_loops
 * > This command will run HiCCUPS on chromosome 22 of HIC006 at 5kB and 10kB resolution using the following values:
 * >> 5kB: fdr 10%, peak width 4, window width 7, and centroid distance 20kB
 * >> 10kB: fdr 10%, peak width 2, window width 5, and centroid distance 20kB
 * > The resulting loop list will be merged and saved as all_hiccups_loops
 * > Note that these are values used for generating the GM12878 loop list
 */
public class HiCCUPS_postproc extends JuicerCLT {

    public static final int krNeighborhood = 5;
    public static final Color defaultPeakColor = Color.cyan;
    public static final boolean shouldColorBeScaledByFDR = false;
    private static final int regionMargin = 20;
    private static final int totalMargin = 2 * regionMargin;
    private static final int w1 = 40;      // TODO dimension should be variably set
    private static final int w2 = 10000;   // TODO dimension should be variably set
    private static final boolean dataShouldBePostProcessed = true;
    private static final String MERGED = "merged_loops";
    private static final String FDR_THRESHOLDS = "fdr_thresholds";
    private static final String ENRICHED_PIXELS = "enriched_pixels";
    private static final String REQUESTED_LIST = "requested_list";
    private static double fdrsum = 0.02;
    private static double oeThreshold1 = 1.5;
    private static double oeThreshold2 = 1.75;
    private static double oeThreshold3 = 2;
    private static int matrixSize = 512;// 540 original
    private static int regionWidth = matrixSize - totalMargin;
    private boolean configurationsSetByUser = false;
    private String featureListPath1;
    private String featureListPath2;
    private boolean listGiven = false;
    private boolean checkMapDensityThreshold = true;

    /*
     * Reasonable Commands
     *
     * fdr = 0.10 for all resolutions
     * peak width = 1 for 25kb, 2 for 10kb, 4 for 5kb
     * window = 3 for 25kb, 5 for 10kb, 7 for 5kb
     *
     * cluster radius is 20kb for 5kb and 10kb res and 50kb for 25kb res
     * fdrsumthreshold is 0.02 for all resolutions
     * oeThreshold1 = 1.5 for all res
     * oeThreshold2 = 1.75 for all res
     * oeThreshold3 = 2 for all res
     *
     * published GM12878 looplist was only generated with 5kb and 10kb resolutions
     * same with published IMR90 looplist
     * published CH12 looplist only generated with 10kb
     */
    private File outputDirectory;
    private List<HiCCUPSConfiguration> configurations;
    private Dataset ds;

    public HiCCUPS_postproc() {
        super("hiccups [-m matrixSize] [-k normalization (NONE/VC/VC_SQRT/KR)] [-c chromosome(s)] [-r resolution(s)] " +
                "[-f fdr] [-p peak width] [-i window] [-t thresholds] [-d centroid distances] [--ignore_sparsity]" +
                "<hicFile> <outputDirectory> [specified_loop_list]");
        Feature2D.allowHiCCUPSOrdering = true;
    }

    public static String getBasicUsage() {
        return "hiccups <hicFile> <outputDirectory>";
    }

    @Override
    protected void readJuicerArguments(String[] args, CommandLineParserForJuicer juicerParser) {
        if (args.length != 3 && args.length != 5) {
            printUsageAndExit();
        }
        // TODO: add code here to check for CUDA/GPU installation. The below is not ideal.

        ds = HiCFileTools.extractDatasetForCLT(Arrays.asList(args[1].split("\\+")), true);
        outputDirectory = HiCFileTools.createValidDirectory(args[2]);

        if (args.length == 5) {
            listGiven = true;
            featureListPath1 = args[3];
            featureListPath2 = args[4];
        }

        NormalizationType preferredNorm = juicerParser.getNormalizationTypeOption();
        if (preferredNorm != null)
            norm = preferredNorm;

        determineValidMatrixSize(juicerParser);
        determineValidConfigurations(juicerParser, ds.getBpZooms());

        if (juicerParser.getBypassMinimumMapCountCheckOption()) {
            checkMapDensityThreshold = false;
        }
    }

    /**
     * todo needs some more development/expansion
     */
    private void testGPUInstallation(){
        try {
            jcuda.Pointer pointer = new jcuda.Pointer();
            JCuda.cudaMalloc(pointer, 4);
            JCuda.cudaFree(pointer);
        }
        catch (Exception e) {
            System.err.println("GPU/CUDA Installation Not Detected");
            System.err.println("Exiting HiCCUPS");
            System.exit(24);
        }
    }

    /**
     * Used by hiccups diff to set the properties of hiccups directly without resorting to command line usage
     *
     * @param inputHiCFileName
     * @param outputDirectoryPath
     * @param featureListPath
     * @param preferredNorm
     * @param matrixSize
     * @param providedCommonChromosomes
     * @param configurations
     * @param thresholds
     */
    public void initializeDirectly(String inputHiCFileName, String outputDirectoryPath,
                                   String featureListPath, NormalizationType preferredNorm, int matrixSize,
                                   List<Chromosome> providedCommonChromosomes,
                                   List<HiCCUPSConfiguration> configurations, double[] thresholds) {
        ds = HiCFileTools.extractDatasetForCLT(Arrays.asList(inputHiCFileName.split("\\+")), true);
        outputDirectory = HiCFileTools.createValidDirectory(outputDirectoryPath);

        if (featureListPath != null) {
            listGiven = true;
            this.featureListPath1 = featureListPath;
        }

        if (preferredNorm != null) norm = preferredNorm;

        // will just confirm matrix size is large enough
        determineValidMatrixSize(matrixSize);

        // configurations should have been passed in
        if (configurations != null && configurations.size() > 0) {
            configurationsSetByUser = true;
            this.configurations = configurations;
        }

        // fdr & oe thresholds directly sent in
        if (thresholds != null) setHiCCUPSFDROEThresholds(thresholds);

        // force hiccups to run
        checkMapDensityThreshold = false;
    }

    @Override
    public void run() {

        Map<Integer, Feature2DList> loopLists = new HashMap<>();
        ChromosomeHandler chromosomeHandler = ds.getChromosomeHandler();
        File outputMergedFile = new File(outputDirectory, MERGED);
        if (dataShouldBePostProcessed) {
         //   loopLists.put(conf.getResolution(), enrichedPixels);
            loopLists.put(5000, Feature2DParser.loadFeatures(featureListPath1, chromosomeHandler, true,
                    null, false));
            loopLists.put(10000, Feature2DParser.loadFeatures(featureListPath2, chromosomeHandler, true,
                    null, false));

            Feature2DList finalList = HiCCUPSUtils.postProcess(loopLists, ds, chromosomeHandler,
                    configurations, norm, outputDirectory);
            finalList.exportFeatureList(outputMergedFile, true, Feature2DList.ListFormat.FINAL);
            System.out.println(finalList.getNumTotalFeatures() + " loops written to file: " +
                    outputMergedFile.getAbsolutePath());
        }
        System.out.println("HiCCUPS complete");
        // else the thresholds and raw pixels were already saved when hiccups was run
    }

    /**
     * Actual run of the HiCCUPS algorithm
     *
     * @param ds                dataset from hic file
     * @param conf              configuration of hiccups inputs
     * @param chromosomeHandler list of chromosomes to run hiccups on
     * @return list of enriched pixels
     */
    private Feature2DList runHiccupsProcessing(Dataset ds, HiCCUPSConfiguration conf,
                                               ChromosomeHandler chromosomeHandler, Feature2DHandler inputListFeature2DHandler) {

        long begin_time = System.currentTimeMillis();

        HiCZoom zoom = ds.getZoomForBPResolution(conf.getResolution());
        if (zoom == null) {
            System.err.println("Data not available at " + conf.getResolution() + " resolution");
            return null;
        }

        // open the print writer early so the file I/O capability is verified before running hiccups
        PrintWriter outputFDR = HiCFileTools.openWriter(
                new File(outputDirectory, FDR_THRESHOLDS + "_" + conf.getResolution()));

        long[][] histBL = new long[w1][w2];
        long[][] histDonut = new long[w1][w2];
        long[][] histH = new long[w1][w2];
        long[][] histV = new long[w1][w2];
        float[][] fdrLogBL = new float[w1][w2];
        float[][] fdrLogDonut = new float[w1][w2];
        float[][] fdrLogH = new float[w1][w2];
        float[][] fdrLogV = new float[w1][w2];
        float[] thresholdBL = ArrayTools.newValueInitializedFloatArray(w1, (float) w2);
        float[] thresholdDonut = ArrayTools.newValueInitializedFloatArray(w1, (float) w2);
        float[] thresholdH = ArrayTools.newValueInitializedFloatArray(w1, (float) w2);
        float[] thresholdV = ArrayTools.newValueInitializedFloatArray(w1, (float) w2);

        GPUController gpuController = null;
        try {
            gpuController = new GPUController(conf.getWindowWidth(), matrixSize,
                    conf.getPeakWidth(), false);
        } catch (Exception e) {
            System.err.println("GPU/CUDA Installation Not Detected");
            System.err.println("Exiting HiCCUPS");
            System.exit(26);
        }


        // to hold all enriched pixels found in second run
        Feature2DList globalList = new Feature2DList();
        Feature2DList requestedList = new Feature2DList();

        // two runs, 1st to build histograms, 2nd to identify loops

        // determine which chromosomes will run
        double maxProgressStatus = determineHowManyChromosomesWillActuallyRun(ds, chromosomeHandler) * 2;

        int currentProgressStatus = 0;
        for (int runNum : new int[]{0, 1}) {
            for (Chromosome chromosome : chromosomeHandler.getChromosomeArrayWithoutAllByAll()) {
                // skip these matrices
                Matrix matrix = ds.getMatrix(chromosome, chromosome);
                if (matrix == null) continue;

                if (HiCGlobals.printVerboseComments) {
                    System.out.println("Processing " + chromosome + " ; run num " + runNum);
                }

                // get matrix data access
                long start_time = System.currentTimeMillis();
                MatrixZoomData zd = matrix.getZoomData(zoom);

                //NormalizationType preferredNormalization = HiCFileTools.determinePreferredNormalization(ds);
                NormalizationVector normVector = ds.getNormalizationVector(chromosome.getIndex(), zoom, norm);
                if (normVector != null) {
                    double[] normalizationVector = normVector.getData();
                    double[] expectedVector = HiCFileTools.extractChromosomeExpectedVector(ds, chromosome.getIndex(),
                            zoom, norm);

                    // need overall bounds for the chromosome
                    int chrLength = chromosome.getLength();
                    int chrMatrixWidth = (int) Math.ceil((double) chrLength / conf.getResolution());
                    double chrWidthInTermsOfMatrixDimension = Math.ceil(chrMatrixWidth * 1.0 / regionWidth) + 1;
                    long load_time = System.currentTimeMillis();
                    if (HiCGlobals.printVerboseComments) {
                        System.out.println("Time to load chr " + chromosome.getName() + " matrix: " + (load_time - start_time) + "ms");
                    }

                    for (int i = 0; i < chrWidthInTermsOfMatrixDimension; i++) {
                        int[] rowBounds = calculateRegionBounds(i, regionWidth, chrMatrixWidth);

                        if (rowBounds[4] < chrMatrixWidth - regionMargin) {
                            for (int j = i; j < chrWidthInTermsOfMatrixDimension; j++) {
                                int[] columnBounds = calculateRegionBounds(j, regionWidth, chrMatrixWidth);
                                if (HiCGlobals.printVerboseComments) {
                                    System.out.print(".");
                                }

                                if (columnBounds[4] < chrMatrixWidth - regionMargin) {
                                    try {
                                        if (HiCGlobals.printVerboseComments) {
                                            System.out.println("");
                                            System.out.println("GPU Run Details");
                                            System.out.println("Row bounds " + Arrays.toString(rowBounds));
                                            System.out.println("Col bounds " + Arrays.toString(columnBounds));
                                        }
                                        GPUOutputContainer gpuOutputs = gpuController.process(zd, normalizationVector, expectedVector,
                                                rowBounds, columnBounds, matrixSize,
                                                thresholdBL, thresholdDonut, thresholdH, thresholdV, norm);

                                        int diagonalCorrection = (rowBounds[4] - columnBounds[4]) + conf.getPeakWidth() + 2;

                                        if (runNum == 0) {
                                            gpuOutputs.cleanUpBinNans();
                                            gpuOutputs.cleanUpBinDiagonal(diagonalCorrection);
                                            gpuOutputs.updateHistograms(histBL, histDonut, histH, histV, w1, w2);

                                        } else if (runNum == 1) {
                                            gpuOutputs.cleanUpPeakNaNs();
                                            gpuOutputs.cleanUpPeakDiagonal(diagonalCorrection);

                                            Feature2DList peaksList = gpuOutputs.extractPeaks(chromosome.getIndex(), chromosome.getName(),
                                                    w1, w2, rowBounds[4], columnBounds[4], conf.getResolution());
                                            Feature2DTools.calculateFDR(peaksList, fdrLogBL, fdrLogDonut, fdrLogH, fdrLogV);
                                            globalList.add(peaksList);

                                            if (listGiven) {
                                                float rowBound1GenomeCoords = ((float) rowBounds[4]) * conf.getResolution();
                                                float columnBound1GenomeCoords = ((float) columnBounds[4]) * conf.getResolution();
                                                float rowBound2GenomeCoords = ((float) rowBounds[5] - 1) * conf.getResolution();
                                                float columnBound2GenomeCoords = ((float) columnBounds[5] - 1) * conf.getResolution();
                                                // System.out.println(chromosome.getIndex() + "\t" + rowBound1GenomeCoords + "\t" + rowBound2GenomeCoords + "\t" + columnBound1GenomeCoords + "\t" + columnBound2GenomeCoords);
                                                net.sf.jsi.Rectangle currentWindow = new net.sf.jsi.Rectangle(rowBound1GenomeCoords,
                                                        columnBound1GenomeCoords, rowBound2GenomeCoords, columnBound2GenomeCoords);
                                                List<Feature2D> inputListFoundFeatures = inputListFeature2DHandler.getContainedFeatures(chromosome.getIndex(), chromosome.getIndex(),
                                                        currentWindow);
                                                Feature2DList peaksRequestedList = gpuOutputs.extractPeaksListGiven(chromosome.getIndex(), chromosome.getName(),
                                                        w1, w2, rowBounds[4], columnBounds[4], conf.getResolution(), inputListFoundFeatures);
                                                Feature2DTools.calculateFDR(peaksRequestedList, fdrLogBL, fdrLogDonut, fdrLogH, fdrLogV);
                                                requestedList.add(peaksRequestedList);
                                            }
                                        }
                                    } catch (IOException e) {
                                        System.err.println("No data in map region");
                                    }
                                }
                            }
                        }
                    }

                    if (HiCGlobals.printVerboseComments) {
                        long segmentTime = System.currentTimeMillis();

                        if (runNum == 0) {
                            System.out.println("Time to calculate chr " + chromosome.getName() + " expecteds and add to hist: " + (segmentTime - load_time) + "ms");
                        } else { // runNum = 1
                            System.out.println("Time to print chr " + chromosome.getName() + " peaks: " + (segmentTime - load_time) + "ms");
                        }
                    }
                } else {
                    System.err.println("Data not available for " + chromosome + " at " + conf.getResolution() + " resolution");
                }

                System.out.println(((int) Math.floor((100.0 * ++currentProgressStatus) / maxProgressStatus)) + "% ");
            }
            if (runNum == 0) {

                long thresh_time0 = System.currentTimeMillis();

                long[][] rcsHistBL = ArrayTools.makeReverse2DCumulativeArray(histBL);
                long[][] rcsHistDonut = ArrayTools.makeReverse2DCumulativeArray(histDonut);
                long[][] rcsHistH = ArrayTools.makeReverse2DCumulativeArray(histH);
                long[][] rcsHistV = ArrayTools.makeReverse2DCumulativeArray(histV);

                for (int i = 0; i < w1; i++) {
                    float[] unitPoissonPMF = Floats.toArray(Doubles.asList(ArrayTools.generatePoissonPMF(i, w2)));
                    HiCCUPSUtils.calculateThresholdAndFDR(i, w2, conf.getFDRThreshold(), unitPoissonPMF, rcsHistBL, thresholdBL, fdrLogBL);
                    HiCCUPSUtils.calculateThresholdAndFDR(i, w2, conf.getFDRThreshold(), unitPoissonPMF, rcsHistDonut, thresholdDonut, fdrLogDonut);
                    HiCCUPSUtils.calculateThresholdAndFDR(i, w2, conf.getFDRThreshold(), unitPoissonPMF, rcsHistH, thresholdH, fdrLogH);
                    HiCCUPSUtils.calculateThresholdAndFDR(i, w2, conf.getFDRThreshold(), unitPoissonPMF, rcsHistV, thresholdV, fdrLogV);
                }

                if (HiCGlobals.printVerboseComments) {
                    long thresh_time1 = System.currentTimeMillis();
                    System.out.println("Time to calculate thresholds: " + (thresh_time1 - thresh_time0) + "ms");
                }
            }

        }

        globalList.exportFeatureList(new File(outputDirectory, ENRICHED_PIXELS + "_" + conf.getResolution() + ".bedpe"),
                true, Feature2DList.ListFormat.ENRICHED);
        if (listGiven) {
            requestedList.exportFeatureList(new File(outputDirectory, REQUESTED_LIST + "_" + conf.getResolution() + ".bedpe"),
                    true, Feature2DList.ListFormat.ENRICHED);
        }
        for (int i = 0; i < w1; i++) {
            outputFDR.println(i + "\t" + thresholdBL[i] + "\t" + thresholdDonut[i] + "\t" + thresholdH[i] +
                    "\t" + thresholdV[i]);
        }
        outputFDR.close();


        if (HiCGlobals.printVerboseComments) {
            long final_time = System.currentTimeMillis();
            System.out.println("Total time: " + (final_time - begin_time));
        }

        return globalList;
    }

    private int[] calculateRegionBounds(int index, int regionWidth, int chrMatrixWidth) {

        int bound1R = Math.min(regionMargin + (index * regionWidth), chrMatrixWidth - regionMargin);
        int bound1 = bound1R - regionMargin;
        int bound2R = Math.min(bound1R + regionWidth, chrMatrixWidth - regionMargin);
        int bound2 = bound2R + regionMargin;

        int diff1 = bound1R - bound1;
        int diff2 = bound2 - bound2R;

        return new int[]{bound1, bound2, diff1, diff2, bound1R, bound2R};
    }

    /**
     * @param juicerParser  Parser to determine configurations
     * @param availableZooms
     */
    private void determineValidConfigurations(CommandLineParserForJuicer juicerParser, List<HiCZoom> availableZooms) {

        configurations = HiCCUPSConfiguration.extractConfigurationsFromCommandLine(juicerParser, availableZooms);
        if (configurations == null) {
            System.out.println("No valid configurations specified, using default settings");
            configurationsSetByUser = false;
        }
        else {
            configurationsSetByUser = true;
        }

        try {
            List<String> t = juicerParser.getThresholdOptions();
            if (t != null && t.size() == 4) {
                double[] thresholds = HiCCUPSUtils.extractDoubleValues(t, 4, Double.NaN);
                setHiCCUPSFDROEThresholds(thresholds);
            }
        } catch (Exception e) {
            // do nothing - use default postprocessing thresholds
        }
    }

    private void determineValidMatrixSize(CommandLineParserForJuicer juicerParser) {
        determineValidMatrixSize(juicerParser.getMatrixSizeOption());
    }

    private void determineValidMatrixSize(int specifiedMatrixSize) {
        if (specifiedMatrixSize > 2 * regionMargin) {
            matrixSize = specifiedMatrixSize;
            regionWidth = specifiedMatrixSize - totalMargin;
        }
        if (HiCGlobals.printVerboseComments) {
            System.out.println("Using Matrix Size " + matrixSize);
        }
    }

    private void setHiCCUPSFDROEThresholds(double[] thresholds) {
        if (thresholds != null && thresholds.length == 4) {
            if (!Double.isNaN(thresholds[0]) && thresholds[0] > 0) fdrsum = thresholds[0];
            if (!Double.isNaN(thresholds[1]) && thresholds[1] > 0) oeThreshold1 = thresholds[1];
            if (!Double.isNaN(thresholds[2]) && thresholds[2] > 0) oeThreshold2 = thresholds[2];
            if (!Double.isNaN(thresholds[3]) && thresholds[3] > 0) oeThreshold3 = thresholds[3];
        }
    }
}