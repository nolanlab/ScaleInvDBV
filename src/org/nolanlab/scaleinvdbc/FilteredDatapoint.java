/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nolanlab.scaleinvdbc;

import clustering.Datapoint;
import java.util.Arrays;
import util.MatrixOp;

/**
 *
 * @author Nikolay
 */
public class FilteredDatapoint extends Datapoint {
    
    public static final String[] filterParamNames= new String[]{
        "Sum of BC channels",
        "Z-score separation of pos and neg channels",
        "Positive to Negative channel ratio",
        "Correlation to Centroid",
    };
    
    public static boolean exposeBCVec;
    
    public Datapoint dp;
    
    private final double [] filterVec;

    public FilteredDatapoint(Datapoint dp) {
        super(null, null, 0);
        this.dp = dp;
        this.filterVec = new double[4];
        Arrays.fill(filterVec, -1);
    }
    
    public double[] getBCVector(){
      return dp.getVector();
    };

    @Override
    public int getID() {
        return dp.getID();
    }
    
    

    public double[] getFilterVec() {
        return filterVec;
    }
    
    public double [] getVector(){
        return exposeBCVec?dp.getVector():filterVec;
    }

    @Override
    public double[] getSideVector() {
        return  dp.getSideVector();
    }

    public void setSumSQ(double sumSQ) {
       filterVec[0] = sumSQ;
    }

    public void setZscoreSep(double ZscoreSep) {
        filterVec[1] = ZscoreSep;
    }

    public void setPosToNegRatio(double PosToNegRatio) {
        filterVec[2] = PosToNegRatio;
    }

    public void setCorrelToCentroid(double CorrelToCentroid) {
        filterVec[3] = CorrelToCentroid;
    }
    
  

    public double getSum() {
        return filterVec[0];
    }

    public double getZscoreSep() {
        return filterVec[1];
    }

    public double getPosToNegRatio() {
        return filterVec[2];
    }

    public double getCorrelToCentroid() {
        return filterVec[3];
    }

 
    
    
}
