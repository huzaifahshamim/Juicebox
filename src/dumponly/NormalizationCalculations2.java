/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2011-2016 Broad Institute, Aiden Lab
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

package dumponly;

import org.apache.commons.math.stat.StatUtils;
import org.broad.igv.util.collections.FloatArrayList;
import org.broad.igv.util.collections.IntArrayList;

import java.util.ArrayList;
import java.util.Arrays;


/**
 * Class for computing VC ("Vanilla Coverage") and KR normalization vector.
 * <p/>
 * Note: currently these are valid for square matrices only.
 *
 * @author jrobinso
 *         Date: 1/25/13
 *         Time: 4:03 PM
 */
class NormalizationCalculations2 {

    private ArrayList<ContactRecord2> list;
    private int totSize;

    public NormalizationCalculations2(ArrayList<ContactRecord2> list, int totSize) {
        this.list = list;
        this.totSize = totSize;
    }


    private static double[] computeKRNormVector(SparseSymmetricMatrix A, double[] x0) {


        int n = x0.length;
        double[] e = new double[n];
        for (int i = 0; i < e.length; i++) e[i] = 1;

        double g = 0.9;
        double etamax = 0.1;
        double eta = etamax;
        double rt = Math.pow(0.000001, 2);

        double[] v = A.multiply(x0);
        double[] rk = new double[v.length];
        for (int i = 0; i < v.length; i++) {
            v[i] = v[i] * x0[i];
            rk[i] = 1 - v[i];
        }
        double rho_km1 = 0;
        for (double aRk : rk) {
            rho_km1 += aRk * aRk;
        }
        double rout = rho_km1;
        double rold = rout;
        int MVP = 0;  // We'll count matrix vector products.

        int not_changing = 0;
        while (rout > rt && not_changing < 100) {    // Outer iteration
            int k = 0;
            double[] y = new double[e.length];
            double[] ynew = new double[e.length];
            double[] Z = new double[e.length];
            double[] p = new double[e.length];
            double[] w = new double[e.length];
            double alpha;
            double beta;
            double gamma;
            double rho_km2 = rho_km1;
            System.arraycopy(e, 0, y, 0, y.length);

            double innertol = Math.max(Math.pow(eta, 2) * rout, rt);
            while (rho_km1 > innertol) {   // Inner iteration by CG
                k++;

                if (k == 1) {
                    rho_km1 = 0;
                    for (int i = 0; i < Z.length; i++) {
                        Z[i] = rk[i] / v[i];
                        p[i] = Z[i];
                        rho_km1 += rk[i] * Z[i];
                    }
                } else {
                    beta = rho_km1 / rho_km2;
                    for (int i = 0; i < p.length; i++) {
                        p[i] = Z[i] + beta * p[i];
                    }
                }
                double[] tmp = new double[e.length];
                for (int i = 0; i < tmp.length; i++) {
                    tmp[i] = x0[i] * p[i];
                }
                tmp = A.multiply(tmp);
                alpha = 0;
                // Update search direction efficiently.
                for (int i = 0; i < tmp.length; i++) {
                    w[i] = x0[i] * tmp[i] + v[i] * p[i];
                    alpha += p[i] * w[i];
                }
                alpha = rho_km1 / alpha;
                double minynew = Double.MAX_VALUE;
                // Test distance to boundary of cone.
                for (int i = 0; i < p.length; i++) {
                    ynew[i] = y[i] + alpha * p[i];
                    if (ynew[i] < minynew) minynew = ynew[i];
                }
                if (minynew <= 0.1) {
                    if (0.1 == 0) break;     // break out of inner loop?
                    gamma = Double.MAX_VALUE;
                    for (int i = 0; i < ynew.length; i++) {
                        if (alpha * p[i] < 0) {
                            if ((0.1 - y[i]) / (alpha * p[i]) < gamma) {
                                gamma = (0.1 - y[i]) / (alpha * p[i]);
                            }
                        }
                    }
                    for (int i = 0; i < y.length; i++)
                        y[i] = y[i] + gamma * alpha * p[i];
                    break;   // break out of inner loop?
                }
                rho_km2 = rho_km1;
                rho_km1 = 0;
                for (int i = 0; i < y.length; i++) {
                    y[i] = ynew[i];
                    rk[i] = rk[i] - alpha * w[i];
                    Z[i] = rk[i] / v[i];
                    rho_km1 += rk[i] * Z[i];
                }

            } // end inner loop
            for (int i = 0; i < x0.length; i++) {
                x0[i] = x0[i] * y[i];
            }
            v = A.multiply(x0);
            rho_km1 = 0;
            for (int i = 0; i < v.length; i++) {
                v[i] = v[i] * x0[i];
                rk[i] = 1 - v[i];
                rho_km1 += rk[i] * rk[i];
            }
            if (Math.abs(rho_km1 - rout) < 0.000001 || Double.isInfinite(rho_km1)) {
                not_changing++;
            }
            rout = rho_km1;
            MVP = MVP + k + 1;
            //  Update inner iteration stopping criterion.
            double rat = rout / rold;
            rold = rout;
            double r_norm = Math.sqrt(rout);
            double eta_o = eta;
            eta = g * rat;
            if (g * Math.pow(eta_o, 2) > 0.1) {
                eta = Math.max(eta, g * Math.pow(eta_o, 2));
            }
            eta = Math.max(Math.min(eta, etamax), 0.5 * 0.000001 / r_norm);
        }
        if (not_changing >= 100) {
            return null;
        }
        return x0;
    }


    public double[] getNorm(NormalizationType2 normOption) {
        double[] norm;
        if (normOption == NormalizationType2.KR || normOption == NormalizationType2.GW_KR || normOption == NormalizationType2.INTER_KR) {
            norm = computeKR();
        } else if (normOption == NormalizationType2.VC || normOption == NormalizationType2.GW_VC || normOption == NormalizationType2.INTER_VC) {
            norm = computeVC();
        } else {
            System.err.println("Not supported for normalization " + normOption);
            return null;
        }

        double factor = getSumFactor(norm);
        for (int i = 0; i < norm.length; i++) {
            norm[i] = norm[i] * factor;
        }
        return norm;
    }

    /**
     * Compute vanilla coverage norm, just the sum of the rows
     *
     * @return Normalization vector
     */
    private double[] computeVC() {
        double[] rowsums = new double[totSize];

        for (int i = 0; i < rowsums.length; i++) rowsums[i] = 0;

        for (ContactRecord2 cr : list) {
            int x = cr.getBinX();
            int y = cr.getBinY();
            float value = cr.getCounts();
            rowsums[x] += value;
            if (x != y) {
                rowsums[y] += value;
            }
        }

        return rowsums;

    }

    /**
     * Get the sum of the normalized matrix
     *
     * @param norm Normalization vector
     * @return Square root of ratio of original to normalized vector
     */
    private double getSumFactor(double[] norm) {
        double matrix_sum = 0;
        double norm_sum = 0;
        for (ContactRecord2 cr : list) {
            int x = cr.getBinX();
            int y = cr.getBinY();
            float value = cr.getCounts();
            if (!Double.isNaN(norm[x]) && !Double.isNaN(norm[y]) && norm[x] > 0 && norm[y] > 0) {
                // want total sum of matrix, not just upper triangle
                if (x == y) {
                    norm_sum += value / (norm[x] * norm[y]);
                    matrix_sum += value;
                } else {
                    norm_sum += 2 * value / (norm[x] * norm[y]);
                    matrix_sum += 2 * value;
                }

            }
        }
        return Math.sqrt(norm_sum / matrix_sum);
    }


    private double[] computeKR() {

        boolean recalculate = true;
        int[] offset = getOffset(0);
        double[] kr = null;
        int iteration = 1;

        while (recalculate && iteration <= 6) {
            // create new matrix upon every iteration, because we've thrown out rows

            SparseSymmetricMatrix sparseMatrix = new SparseSymmetricMatrix();
            populateMatrix(sparseMatrix, offset);

            // newSize is size of new sparse matrix (non-sparse rows)
            int newSize = 0;
            for (int offset1 : offset) {
                if (offset1 != -1) newSize++;
            }

            // initialize x0 for call the compute KR norm
            double[] x0 = new double[newSize];
            for (int i = 0; i < x0.length; i++) x0[i] = 1;

            x0 = computeKRNormVector(sparseMatrix, x0);

            // assume all went well and we don't need to recalculate
            recalculate = false;
            //int rowsTossed = 0;

            if (x0 == null || iteration == 5) {
                // if x0 is no good, throw out some percentage of rows and reset the offset array that gives those rows
                recalculate = true;
                if (iteration < 5) {
                    offset = getOffset(iteration);
                } else {
                    offset = getOffset(10);
                }
                //   System.out.print(" " + iteration + "%");
            } else {
                // otherwise, check to be sure there are no tiny KR values
                // create true KR vector
                kr = new double[totSize];
                int krIndex = 0;
                for (int offset1 : offset) {
                    if (offset1 == -1) {
                        kr[krIndex++] = Double.NaN;
                    } else {
                        kr[krIndex++] = (1.0 / x0[offset1]);
                    }
                }
                // find scaling factor
                double mySum = getSumFactor(kr);

                // if any values are too small, recalculate.  set those rows to be thrown out and reset the offset
                // note that if no rows are thrown out, the offset should not change
                int index = 0;
                for (int i = 0; i < kr.length; i++) {
                    if (kr[i] * mySum < 0.01) {
                        offset[i] = -1;
                        //rowsTossed++;
                        recalculate = true;
                    } else {
                        if (offset[i] != -1) offset[i] = index++;
                    }
                }
                // if (recalculate) System.out.print(" " + rowsTossed);
            }
            iteration++;

        }
        if (iteration > 6 && recalculate) {
            kr = new double[totSize];
            for (int i = 0; i < totSize; i++) {
                kr[i] = Double.NaN;
            }
        }

        return kr;

    }

    private int[] getOffset(double percent) {
        double[] rowSums = new double[totSize];

        for (int i = 0; i < rowSums.length; i++) rowSums[i] = 0;

        for (ContactRecord2 cr : list) {
            int x = cr.getBinX();
            int y = cr.getBinY();
            float value = cr.getCounts();
            rowSums[x] += value;
            if (x != y) {
                rowSums[y] += value;
            }
        }

        double thresh = 0;
        if (percent > 0) {
            // Get percent threshold from positive row sums (nonzero)
            int j = 0;
            for (double sum : rowSums) if (sum != 0) j++;
            double[] posRowSums = new double[j];
            j = 0;
            for (double sum : rowSums) if (sum != 0) posRowSums[j++] = sum;
            thresh = StatUtils.percentile(posRowSums, percent);
        }
        int[] offset = new int[rowSums.length];
        int index = 0;
        for (int i = 0; i < rowSums.length; i++) {
            if (rowSums[i] <= thresh) {
                offset[i] = -1;
            } else {
                offset[i] = index++;
            }
        }

        return offset;

    }

    private void populateMatrix(SparseSymmetricMatrix A, int[] offset) {
        for (ContactRecord2 cr : list) {
            int x = cr.getBinX();
            int y = cr.getBinY();
            float value = cr.getCounts();
            if (offset[x] != -1 && offset[y] != -1) {
                A.set(offset[x], offset[y], value);
            }
        }
    }

    /**
     * Represents a sparse, symmetric matrix in the sense that value(x,y) == value(y,x).  It is an error to
     * add an x,y value twice, or to add both x,y and y,x, although this is not checked.   The class is designed
     * for minimum memory footprint and good performance for vector multiplication, it is not a general purpose
     * matrix class.   It is not private only so it can be unit tested
     */
    // TODO - should we make this its own class? able to do Pearson's and gradient?
    static class SparseSymmetricMatrix {

        IntArrayList rows1 = null;
        IntArrayList cols1 = null;
        FloatArrayList values1 = null;
        IntArrayList rows2 = null;
        IntArrayList cols2 = null;
        FloatArrayList values2 = null;


        public SparseSymmetricMatrix() {
            rows1 = new IntArrayList();
            cols1 = new IntArrayList();
            values1 = new FloatArrayList();
        }

        public void set(int row, int col, float v) {

            if (!Float.isNaN(v)) {
                if (rows2 == null) {
                    try {
                        rows1.add(row);
                        cols1.add(col);
                        values1.add(v);
                    } catch (NegativeArraySizeException error) {
                        rows2 = new IntArrayList();
                        cols2 = new IntArrayList();
                        values2 = new FloatArrayList();
                        rows2.add(row);
                        cols2.add(col);
                        values2.add(v);
                    }
                } else {
                    rows2.add(row);
                    cols2.add(col);
                    values2.add(v);
                }
            }
        }


        public double[] multiply(double[] vector) {

            double[] result = new double[vector.length];
            Arrays.fill(result, 0);

            int[] rowArray1 = rows1.toArray();
            int[] colArray1 = cols1.toArray();
            float[] valueArray1 = values1.toArray();

            int n = rowArray1.length;
            for (int i = 0; i < n; i++) {
                int row = rowArray1[i];
                int col = colArray1[i];
                float value = valueArray1[i];
                result[row] += vector[col] * value;

                if (row != col) {
                    result[col] += vector[row] * value;
                }
            }
            if (rows2 != null) {
                int[] rowArray2 = rows2.toArray();
                int[] colArray2 = cols2.toArray();
                float[] valueArray2 = values2.toArray();
                int n2 = rowArray2.length;
                for (int j = 0; j < n2; j++) {
                    int row = rowArray2[j];
                    int col = colArray2[j];
                    float value = valueArray2[j];
                    result[row] += vector[col] * value;

                    if (row != col) {
                        result[col] += vector[row] * value;
                    }
                }
            }

            return result;
        }
    }

}
