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
package unipg.gila.layout.force;

import org.apache.log4j.Logger;

import unipg.gila.utils.Toolbox;

/**
 * @author Alessio Arleo
 *
 */
public class FR extends Force {
	
	Logger log = Logger.getLogger(this.getClass());
	
	/**
	 * Parameter-less constructor.
	 */
	public FR() {

	}

	/* (non-Javadoc)
	 * @see unipg.dafne.layout.force.Force#generateForce(java.lang.String[])
	 */
	@Override
	public void generateForce(String[] args) {
	}

	/* (non-Javadoc)
	 * @see unipg.dafne.layout.force.Force#computeAttractiveForce(float[], float)
	 */
	@Override
	public float[] computeAttractiveForce(float deltaX, float deltaY, float distance, float squareDistance, float desiredDistance, int v1Deg, int v2Deg) {
		return new float[]{
				deltaX*distance/desiredDistance,
				deltaY*distance/desiredDistance};
		//		return squareDistance/k;
	}

	/* (non-Javadoc)
	 * @see unipg.dafne.layout.force.Force#computeRepulsiveForce(float[], float)
	 */
	@Override
	public float[] computeRepulsiveForce(float deltaX, float deltaY, float distance, float squareDistance, int v1Deg, int v2Deg) {
//		float degCorrection = v2Deg/(float)v1Deg;
		float degCorrection = 1.0f;
		return new float[]{
				degCorrection*(deltaX/Toolbox.floatFuzzyMath(squareDistance)),
				degCorrection*(deltaY/Toolbox.floatFuzzyMath(squareDistance))};
//		return 1/distance;
	}
}
