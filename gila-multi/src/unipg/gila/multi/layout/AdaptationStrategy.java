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
public interface AdaptationStrategy {

	public int returnCurrentK(int currentLayer, int nOfLayers, int nOfVerticesOfLayer, int nOfEdgesOfLayer, int workers);

	public double returnCurrentInitialTempFactor(int currentLayer, int nOfLayers, int nOfVerticesOfLayer, int nOfEdgesOfLayer);

	public double returnCurrentCoolingSpeed(int currentLayer, int nOfLayers, int nOfVerticesOfLayer, int nOfEdgesOfLayer);
	
	public double returnTargetAccuracyy(int currentLayer, int nOfLayers, int nOfVerticesOfLayer, int nOfEdgesOfLayer);


}
