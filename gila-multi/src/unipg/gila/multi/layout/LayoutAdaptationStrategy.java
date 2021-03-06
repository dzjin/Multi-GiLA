/*******************************************************************************
 * Copyright 2016 Alessio Arleo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
/**
 * 
 */
package unipg.gila.multi.layout;

/**
 * @author Alessio Arleo
 *
 */

public abstract class LayoutAdaptationStrategy implements AdaptationStrategy{

  public static int maxK = 6;
  public static float maxAccuracy = 0.0001f;
  public static float minCoolingSpeed = 0.98f;
  public static float minInitialTempFactor = 0.8f;

  /* (non-Javadoc)
   * @see unipg.gila.multi.layout.AdaptationStrategy#returnCurrentInitialTempFactor(int, int, int, int)
   */
  public float returnCurrentInitialTempFactor(int currentLayer,
    int nOfLayers, int nOfVerticesOfLayer, int nOfEdgesOfLayer) {
    if(nOfEdgesOfLayer < 250)
      return 0.1f;
    if(nOfEdgesOfLayer < 500)
      return 0.2f;
    if(nOfEdgesOfLayer < 10000)
      return 0.4f;
    return LayoutAdaptationStrategy.minInitialTempFactor;
  }

  /* (non-Javadoc)
   * @see unipg.gila.multi.layout.AdaptationStrategy#returnCurrentCoolingSpeed(int, int, int, int)
   */
  public float returnCurrentCoolingSpeed(int currentLayer,
    int nOfLayers, int nOfVerticesOfLayer, int nOfEdgesOfLayer) {
    if(nOfEdgesOfLayer < 500)
      return LayoutAdaptationStrategy.minCoolingSpeed;
    if(nOfEdgesOfLayer < 1500)
      return 0.96f;
    if(nOfEdgesOfLayer < 10000)
      return 0.94f;
    if(nOfEdgesOfLayer < 1000000)
      return 0.92f;
    return 0.9f;
  }

  /* (non-Javadoc)
   * @see unipg.gila.multi.layout.AdaptationStrategy#returnTargetAccuracyy(int, int, int, int)
   */
  public float returnTargetAccuracyy(int currentLayer, int nOfLayers,
    int nOfVerticesOfLayer, int nOfEdgesOfLayer) {
    if(nOfEdgesOfLayer < 1000)
      return LayoutAdaptationStrategy.maxAccuracy;
    if(nOfEdgesOfLayer < 10000)
      return 0.001f;
    if(nOfEdgesOfLayer < 1000000)
      return 0.01f;
    return 0.1f;
  }

  public static class DensityDrivenAdaptationStrategy extends LayoutAdaptationStrategy{

    /* (non-Javadoc)
     * @see unipg.gila.multi.layout.LayoutAdaptationStrategy.LayoutAdaptation#returnCurrentK(int, int, int, int)
     */
    public int returnCurrentK(int currentLayer, int nOfLayers,
      int nOfVerticesOfLayer, int nOfEdgesOfLayer) {
      float density = nOfEdgesOfLayer/(float)nOfVerticesOfLayer;
      if(density > 1.5f)
        return 3;
      if(density > 2.5f)
        return 2;
      if(density > 4f)
        return 1;
      return LayoutAdaptationStrategy.maxK;
    }


  }

  public static class SizeDrivenAdaptationStrategy extends LayoutAdaptationStrategy{

    /* (non-Javadoc)
     * @see unipg.gila.multi.layout.LayoutAdaptationStrategy.LayoutAdaptation#returnCurrentK(int, int, int, int)
     */
    public int returnCurrentK(int currentLayer, int nOfLayers,
      int nOfVerticesOfLayer, int nOfEdgesOfLayer) {
      if(nOfEdgesOfLayer < 1000)
        return LayoutAdaptationStrategy.maxK;
      if(nOfEdgesOfLayer < 5000)
        return 5;
      if(nOfEdgesOfLayer < 10000)
        return 4;
      if(nOfEdgesOfLayer > 1000000)
        return 1;
      if(nOfEdgesOfLayer > 100000)
        return 2;
      return 3;
    }

  }

}