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
package unipg.gila.layout;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.giraph.aggregators.BooleanAndAggregator;
import org.apache.giraph.aggregators.FloatMaxAggregator;
import org.apache.giraph.aggregators.IntMaxAggregator;
import org.apache.giraph.aggregators.LongSumAggregator;
import org.apache.giraph.graph.AbstractComputation;
import org.apache.giraph.graph.Computation;
import org.apache.giraph.graph.Vertex;
import org.apache.giraph.master.DefaultMasterCompute;
import org.apache.giraph.master.MasterCompute;
import org.apache.hadoop.io.BooleanWritable;
import org.apache.hadoop.io.FloatWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.MapWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Writable;

import unipg.gila.aggregators.SetAggregator;
import unipg.gila.aggregators.ComponentAggregatorAbstract.ComponentFloatXYMaxAggregator;
import unipg.gila.aggregators.ComponentAggregatorAbstract.ComponentFloatXYMinAggregator;
import unipg.gila.aggregators.ComponentAggregatorAbstract.ComponentIntSumAggregator;
import unipg.gila.aggregators.ComponentAggregatorAbstract.ComponentMapOverwriteAggregator;
import unipg.gila.common.coordinatewritables.CoordinateWritable;
import unipg.gila.common.datastructures.FloatWritableArray;
import unipg.gila.common.datastructures.PartitionedLongWritable;
import unipg.gila.common.datastructures.messagetypes.LayoutMessage;
import unipg.gila.coolingstrategies.CoolingStrategy;
import unipg.gila.coolingstrategies.LinearCoolingStrategy;
import unipg.gila.layout.GraphReintegration.FairShareReintegrateOneEdges;
import unipg.gila.layout.GraphReintegration.PlainDummyComputation;
import unipg.gila.layout.LayoutRoutine.DrawingBoundariesExplorer.DrawingBoundariesExplorerWithComponentsNo;
import unipg.gila.layout.single.SingleScaleLayout.Seeder;
import unipg.gila.utils.Toolbox;

import com.google.common.collect.Lists;

/**
 * This class defines the behaviour of the layout phase of the algorithm, loading the appropriate computations at the right time. It defines
 * the stopping conditions, changes between the seeding and propagating phases and finally reintegrate the one-degree vertices before 
 * halting the computation.
 * 
 * @author general
 *
 */
@SuppressWarnings("rawtypes")
public class LayoutRoutine {
		
	//#############CLINT OPTIONS
	
	//COMPUTATION OPTIONS
	public static final String ttlMaxString = "layout.flooding.ttlMax";
	public static final String computationLimit = "layout.limit";
	public static final String convergenceThresholdString = "layout.convergence-threshold";
	public static final int ttlMaxDefault = 3;
	public static final int maxSstepsDefault = 1500;
	public static final float defaultConvergenceThreshold = 0.85f;

	//MESSAGES OPTIONS
	public static final String useQueuesString = "flooding.useQueues";
	public static final String queueUnloadFactor = "layout.queueUnloadFactor";
	public static final float queueUnloadFactorDefault = 0.1f;

	//REINTEGRATION OPTIONS
	public static final String radiusString = "reintegration.radius";
	public static final String dynamicRadiusString = "reintegration.dynamicRadius";
	public static final String coneWidth = "reintegration.coneWidth";
	public static final String paddingString = "reintegration.anglePadding";
	public static final String oneDegreeReintegratingClassOption = "reintegration.reintegratingClass";
	public static final String componentPaddingConfString = "reintegration.componentPadding";
	public static final String minimalAngularResolutionString = "reintegration.minimalAngularResolution";
	public static final String lowThresholdString = "reintegration.fairLowThreshold";
	public static final float lowThresholdDefault = 2.0f;
	public static final float defaultPadding = 20.0f;
	public static final float radiusDefault = 0.2f;	
	public static final float coneWidthDefault = 90.0f;	
	
	//DRAWING OPTIONS
	public final static String node_length = "layout.node_length";
	public final static String node_width = "layout.node_width";
	public final static String node_separation = "layout.node_separation";
	public final String initialTempFactorString = "layout.initialTempFactor";
	public static final String coolingSpeed = "layout.coolingSpeed";
	public static final String walshawModifierString = "layout.walshawModifier";
	public static final String accuracyString = "layout.accuracy";
	public static final float walshawModifierDefault = 0.052f;
	public final static float defaultNodeValue = 20.0f;
	public float defaultInitialTempFactor = 0.4f;
	public final String defaultCoolingSpeed = "0.93";
	public static final float accuracyDefault = 0.01f;
	public static final String forceMethodOptionString = "layout.forceModel";
	public static final String forceMethodOptionExtraOptionsString = "layout.forceModel.extraOptions";
	public static final String sendDegTooOptionString = "layout.sendDegreeIntoLayoutMessages";
	private static final String repulsiveForceModerationString = "layout.repulsiveForceModerationFactor";	
	
	//INPUT OPTIONS
	public static final String bbString = "layout.boundingBox";
	public static final String randomPlacementString = "layout.randomPlacement";
	
	//OUTPUT OPTIONS
	public static final String showPartitioningString = "layout.output.showPartitioning";
	public static final String showComponentString = "layout.output.showComponent";
	
	//AGGREGATORS
	public static final String convergenceAggregatorString = "AGG_TEMPERATURE";
	public static final String MessagesAggregatorString = "AGG_MESSAGES";
	public static final String maxOneDegAggregatorString = "AGG_ONEDEG_MAX";
	public final static String k_agg = "K_AGG";
	static final String walshawConstant_agg = "WALSHAW_AGG";
	private final static String maxCoords = "AGG_MAX_COORDINATES";
	private final static String minCoords = "AGG_MIN_COORDINATES";
	public final static String tempAGG = "AGG_TEMP";
	public static final String correctedSizeAGG = "AGG_CORR_SIZE";
	protected final static String scaleFactorAgg = "AGG_SCALEFACTOR";
	protected final static String componentNumber = "AGG_COMP_NUMBER";
	protected final static String componentNoOfNodes = "AGG_COMPONENT_NO_OF_NODES";
	public static final String tempAggregator = "AGG_TEMP";
	protected static final String offsetsAggregator = "AGG_CC_BOXES";
	
	//COUNTERS
	protected static final String COUNTER_GROUP = "Drawing Counters";
	
	private static String minRationThresholdString = "layout.minRatioThreshold";
	private float defaultMinRatioThreshold = 0.2f;

	//VARIABLES
	protected long propagationSteps;
	protected long allVertices;
	protected float threshold;
	protected boolean halting;
	long settledSteps;
	protected int readyToSleep;
	protected CoolingStrategy coolingStrategy;
	static int maxSuperstep;
	
	protected MasterCompute master;
	protected Class<? extends AbstractSeeder> seeder;
	protected Class<? extends AbstractPropagator> propagator;
	protected Class<? extends DrawingBoundariesExplorer> drawingExplorer;
	protected Class<? extends DrawingBoundariesExplorerWithComponentsNo> drawingExplorerWithCCs;
	protected Class<? extends DrawingScaler> drawingScaler;
	protected Class<? extends LayoutCCs> layoutCC;
	protected Class<? extends Computation> dummyComputation;
	
	public void initialize(MasterCompute myMaster, Class<? extends AbstractSeeder> seeder, Class<? extends AbstractPropagator> propagator,
			Class<? extends DrawingBoundariesExplorer> explorer, Class<? extends DrawingBoundariesExplorerWithComponentsNo> explorerWithCCs,
			Class<? extends DrawingScaler> drawingScaler,
			Class<? extends LayoutCCs> layoutCC,
			Class<? extends Computation> dummyComputation) throws InstantiationException,
	IllegalAccessException {
		master = myMaster;
		this.seeder = seeder;
		this.propagator = propagator;
		drawingExplorer = explorer;
		drawingExplorerWithCCs = explorerWithCCs;
		this.layoutCC = layoutCC;
		this.dummyComputation = dummyComputation;
		this.drawingScaler = drawingScaler;
		
		maxSuperstep = master.getConf().getInt(computationLimit, maxSstepsDefault);

		threshold = master.getConf().getFloat(convergenceThresholdString, defaultConvergenceThreshold);

		master.registerAggregator(convergenceAggregatorString, LongSumAggregator.class);
		master.registerAggregator(MessagesAggregatorString, BooleanAndAggregator.class);

		master.registerPersistentAggregator(maxOneDegAggregatorString, IntMaxAggregator.class);

		settledSteps = 0;
		halting = false;

		// FRAME AGGREGATORS

		master.registerPersistentAggregator(correctedSizeAGG, ComponentMapOverwriteAggregator.class);

		// TEMP AGGREGATORS

		master.registerPersistentAggregator(tempAGG, ComponentMapOverwriteAggregator.class);

		// COORDINATES AGGREGATORS

		master.registerPersistentAggregator(maxCoords, ComponentFloatXYMaxAggregator.class);
		master.registerPersistentAggregator(minCoords, ComponentFloatXYMinAggregator.class);
		master.registerAggregator(scaleFactorAgg, ComponentMapOverwriteAggregator.class);

		// CONSTANT AGGREGATORS

		master.registerPersistentAggregator(k_agg, FloatMaxAggregator.class);		
		master.registerPersistentAggregator(walshawConstant_agg, FloatMaxAggregator.class);	
		
		//COMPONENT DATA AGGREGATORS
		
		master.registerPersistentAggregator(componentNumber, SetAggregator.class);
		master.registerPersistentAggregator(componentNoOfNodes, ComponentIntSumAggregator.class);
		master.registerAggregator(offsetsAggregator, ComponentMapOverwriteAggregator.class);

		float nl = master.getConf().getFloat(node_length ,defaultNodeValue);
		float nw = master.getConf().getFloat(node_width ,defaultNodeValue);
		float ns = master.getConf().getFloat(node_separation ,defaultNodeValue);
		float k = new Double(ns + Toolbox.computeModule(new float[]{nl, nw})).floatValue();
		master.setAggregatedValue(k_agg, new FloatWritable(k));
		
		float walshawModifier = master.getConf().getFloat(walshawModifierString, walshawModifierDefault);
		
		master.setAggregatedValue(walshawConstant_agg, 
				new FloatWritable(master.getConf().getFloat(repulsiveForceModerationString,(float) (Math.pow(k, 2) * walshawModifier))));
		
		coolingStrategy = new LinearCoolingStrategy(new String[]{master.getConf().get(LayoutRoutine.coolingSpeed, defaultCoolingSpeed )});
	}

	/**
	 * This method executes a number of tasks to tune the algorithm given the proportions of the initial (random) layout of each component.
	 * 
	 * @throws IllegalAccessException
	 */
	protected void superstepOneSpecials() throws IllegalAccessException{
		
		MapWritable aggregatedMaxComponentData = master.getAggregatedValue(maxCoords);
		MapWritable aggregatedMinComponentData = master.getAggregatedValue(minCoords);
		MapWritable componentNodesMap = master.getAggregatedValue(componentNoOfNodes);

		Iterator<Entry<Writable, Writable>> iteratorOverComponents = aggregatedMaxComponentData.entrySet().iterator();

		float k = ((FloatWritable)master.getAggregatedValue(k_agg)).get();
		float tempConstant = master.getConf().getFloat(initialTempFactorString, defaultInitialTempFactor);
		
		MapWritable correctedSizeMap = new MapWritable();
		MapWritable tempMap = new MapWritable();
		MapWritable scaleFactorMap = new MapWritable();

		while(iteratorOverComponents.hasNext()){
			Entry<Writable, Writable> currentEntryMax = iteratorOverComponents.next();
			
			Writable key = currentEntryMax.getKey();
			
			float[] maxCurrent = ((FloatWritableArray)currentEntryMax.getValue()).get();
			float[] minCurrent = ((FloatWritableArray)aggregatedMinComponentData.get(key)).get();
			
			int noOfNodes = ((IntWritable)componentNodesMap.get(key)).get();
			
			float w = (maxCurrent[0] - minCurrent[0]) + k;
			float h = (maxCurrent[1] - minCurrent[1]) + k;
						
			float ratio = h/w;
			float W = new Double(Math.sqrt(noOfNodes/ratio)*k).floatValue();	
			float H = ratio*W;

			float[] correctedSizes = new float[]{W, H};
			float[] scaleFactors = new float[]{W/w, H/h};
			float[] temps = new float[]{W/tempConstant, H/tempConstant};
			
			correctedSizeMap.put(key, new FloatWritableArray(correctedSizes));
			tempMap.put(key, new FloatWritableArray(temps));
			scaleFactorMap.put(key, new FloatWritableArray(scaleFactors));
			
		}
		
		master.setAggregatedValue(correctedSizeAGG, correctedSizeMap);
		master.setAggregatedValue(tempAGG, tempMap);
		master.setAggregatedValue(scaleFactorAgg, scaleFactorMap);
	}

	/**
	 * Convenience method to update the temperature aggregator each time a new seeding phase is performed.
	 */
	protected void updateTemperatureAggregator(){
		MapWritable tempMap = master.getAggregatedValue(tempAGG);
		Iterator<Entry<Writable, Writable>> tempsIterator = tempMap.entrySet().iterator();
		MapWritable newTempsMap = new MapWritable();

		while(tempsIterator.hasNext()){
			Entry<Writable, Writable> currentTemp = tempsIterator.next();
			float[] temps = ((FloatWritableArray)currentTemp.getValue()).get();
			newTempsMap.put(currentTemp.getKey(), new FloatWritableArray(new float[]{coolingStrategy.cool(temps[0]),
																					 coolingStrategy.cool(temps[1])}));
			
		}		
		master.setAggregatedValue(tempAGG, newTempsMap);
	}
	
	/**
	 * The method is used to start the halting sequence and to manage the order of the events leading to the algorithm conclusion.
	 * 
	 * @throws IllegalAccessException
	 */
	protected boolean masterHaltingSequence(){
		if(readyToSleep != 0 || checkForConvergence()){ //IF TRUE, THE HALTING SEQUENCE IS IN PROGRESS
			halting = true;
			if(readyToSleep == 0){ //FIRST STEP: ONE DEGREE VERTICES REINTEGRATION
				try {
					master.setComputation((Class<? extends Computation>)Class.forName(master.getConf().get(oneDegreeReintegratingClassOption, FairShareReintegrateOneEdges.class.toString())));
				} catch (ClassNotFoundException e) {
					master.setComputation(FairShareReintegrateOneEdges.class);
				}	
				readyToSleep++;								
				return false;
			}
			if(readyToSleep == 1){ //A BLANK COMPUTATION TO PROPAGATE THE GRAPH MODIFICATIONS MADE IN THE PREVIOUS SUPERSTEP
				master.setComputation(dummyComputation);
				readyToSleep++;
				return false;
			}
			if(readyToSleep == 2){ //SECOND STEP: TO COMPUTE THE FINAL GRID LAYOUT OF THE CONNECTED COMPONENTS, THEIR DRAWING
				master.setAggregatedValue(maxCoords, new MapWritable()); //PROPORTIONS ARE SCANNED.
				master.setAggregatedValue(minCoords, new MapWritable());
				master.setComputation(drawingExplorer);
				readyToSleep++;
				return false;
			}
			if(readyToSleep == 3){ //THIRD STEP: ONCE THE DATA NEEDED TO LAYOUT THE CONNECTED COMPONENTS GRID ARE COMPUTED, 
				computeComponentGridLayout(); //THE LAYOUT IS COMPUTED.
				master.setComputation(layoutCC);			
				readyToSleep++;
				return false;
			}
			
			return true; //THE SEQUENCE IS COMPLETED, THE COMPUTATION MAY NOW HALT.
//			haltComputation(); //THE SEQUENCE IS COMPLETED, THE COMPUTATION MAY NOW HALT.
		}
		return false;
	}

	/**
	 * 
	 * The main master compute method. 
	 * 
	 */
	public boolean compute(){
		if(master.getSuperstep() == 0){
			return false;
		}
		
		if(masterHaltingSequence())
			return true; //CHECK IF THE HALTING SEQUENCE IS IN PROGRESS

		if(halting) //IF IT IS, THIS STEP MASTER COMPUTATION ENDS HERE.
			return false;
		
		if(master.getSuperstep() == 1){
			try {
				superstepOneSpecials(); //COMPUTE THE FACTORS TO PREPARE THE GRAPH FOR THE LAYOUT.
					master.setComputation(drawingScaler); //... AND APPLY THEM
					return false;
			} catch (IllegalAccessException e) {
				master.haltComputation();
				return true;
			}
		}		
		
		//REGIME COMPUTATION
		if(((BooleanWritable)master.getAggregatedValue(MessagesAggregatorString)).get() && !(master.getComputation().toString().contains("Seeder"))){
			if(settledSteps > 0)
				updateTemperatureAggregator();	//COOL DOWN THE TEMPERATURE
			master.setComputation(seeder); //PERFORM THE LAYOUT UPDATE AND SEEDING
			settledSteps++;
		}else
			if(!(master.getComputation().toString().contains("Propagator"))){
				master.setComputation(propagator); //PROPAGATE THE MESSAGES AND COMPUTE THE FORCES
			}	

		return false;
	}

	/**
	 * Check for graph equilibrium.
	 * @return true if the number of vertices which did not move above the threshold is higher than the convergence
	 * threshold.
	 */
	protected boolean checkForConvergence(){
		if(allVertices <= 0){
			allVertices = master.getTotalNumVertices();
			return false;
		}
		return ((LongWritable)master.getAggregatedValue(convergenceAggregatorString)).get()/allVertices > threshold;
	}
	
	/**
	 * This method computes the connected components final grid layout.
	 */
	protected void computeComponentGridLayout() {
		
		float componentPadding = master.getConf().getFloat(LayoutRoutine.componentPaddingConfString, defaultPadding);
		float minRatioThreshold = master.getConf().getFloat(LayoutRoutine.minRationThresholdString, defaultMinRatioThreshold );
		
		MapWritable offsets = new MapWritable();
				
		MapWritable maxCoordsMap = master.getAggregatedValue(maxCoords);
		MapWritable minCoordsMap = master.getAggregatedValue(minCoords);
		MapWritable componentsNo = master.getAggregatedValue(componentNoOfNodes);
		
		//##### SORTER -- THE MAP CONTAINING THE COMPONENTS' SIZES IS SORTED BY ITS VALUES
		
		LinkedHashMap<LongWritable, IntWritable> sortedMap = (LinkedHashMap<LongWritable, IntWritable>)sortMapByValues(componentsNo);
	
		LongWritable[] componentSizeSorter = sortedMap.keySet().toArray(new LongWritable[0]);
		IntWritable[] componentSizeSorterValues = sortedMap.values().toArray(new IntWritable[0]);
				
		int coloumnNo = new Double(Math.ceil(Math.sqrt(componentsNo.size() - 1))).intValue();
		
		Point2D.Float cursor = new Point2D.Float(0.0f, 0.0f);
		Point2D.Float tableOrigin = new Point2D.Float(0.0f, 0.0f);
		
		Long maxID = componentSizeSorter[componentSizeSorter.length-1].get();
		int maxNo = componentSizeSorterValues[componentSizeSorter.length-1].get();// ((LongWritable)componentsNo.get(new LongWritable(maxID))).get();
		
		float[] translationCorrection = ((FloatWritableArray)minCoordsMap.get(new LongWritable(maxID))).get();
		offsets.put(new LongWritable(maxID), new FloatWritableArray(new float[]{-translationCorrection[0], -translationCorrection[1], 1.0f, cursor.x, cursor.y}));
		
		float[] maxComponents = ((FloatWritableArray)maxCoordsMap.get(new LongWritable(maxID))).get();
//		float componentPadding = getConf().getFloat(FloodingMaster.componentPaddingConfString, defaultPadding)*maxComponents[0];
		cursor.setLocation((maxComponents[0] - translationCorrection[0]) + componentPadding, 0.0f); //THE BIGGEST COMPONENT IS PLACED IN THE UPPER LEFT CORNER.
		tableOrigin.setLocation(cursor);
		
		float coloumnMaxY = 0.0f;
		int counter = 1;
		
		for(int j=componentSizeSorter.length-2; j>=0; j--){ //THE OTHER SMALLER COMPONENTS ARE ARRANGED IN A GRID.
			long currentComponent = componentSizeSorter[j].get();
			maxComponents = ((FloatWritableArray)maxCoordsMap.get(new LongWritable(currentComponent))).get();
			float sizeRatio = (float)componentSizeSorterValues[j].get()/maxNo;
			translationCorrection = ((FloatWritableArray)minCoordsMap.get(new LongWritable(currentComponent))).get();
						
			if(sizeRatio < minRatioThreshold)	
				sizeRatio = minRatioThreshold;
			
			maxComponents[0] -= translationCorrection[0];
			maxComponents[1] -= translationCorrection[1];
			maxComponents[0] *= sizeRatio;
			maxComponents[1] *= sizeRatio;
			
			offsets.put(new LongWritable(currentComponent), new FloatWritableArray(new float[]{-translationCorrection[0], -translationCorrection[1], sizeRatio,  cursor.x, cursor.y}));
			if(maxComponents[1] > coloumnMaxY)
				coloumnMaxY = maxComponents[1];
			if(counter % coloumnNo != 0){
				cursor.setLocation(cursor.x + maxComponents[0] + componentPadding, cursor.y);
				counter++;
			}else{
				cursor.setLocation(tableOrigin.x, cursor.y + coloumnMaxY + componentPadding);
				coloumnMaxY = 0.0f;
				counter = 1;
			}
		}
		master.setAggregatedValue(offsetsAggregator, offsets); //THE VALUES COMPUTED TO LAYOUT THE COMPONENTS ARE STORED INTO AN AGGREGATOR.
	}
	
	/**
	 * This method sorts a map by its values.
	 * 
	 * @param mapToSort
	 * @return The sorted LinkedHashMap.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected static LinkedHashMap sortMapByValues(Map mapToSort){
		List keys = Lists.newArrayList(mapToSort.keySet());
		List values = Lists.newArrayList(mapToSort.values());
				
		Collections.sort(keys);
		Collections.sort(values);	
		
		LinkedHashMap sortedMap = new LinkedHashMap(mapToSort.size());
		
		Iterator vals = values.iterator();
		
		while(vals.hasNext()){
			Object currentVal = vals.next();
			Iterator keysToIterate = keys.iterator();
			
			while(keysToIterate.hasNext()){
				Object currentKey = keysToIterate.next();
				if(mapToSort.get(currentKey).equals(currentVal)){
					sortedMap.put(currentKey, currentVal);
					keys.remove(currentKey);
					break;
				}	
			}
		}
		
		return sortedMap;
		
	}

	/**
	 * In this computation each vertex simply aggregates its coordinates to the max and min coodinates aggregator of its component.
	 * 
	 * @author Alessio Arleo
	 *
	 */
	public static class DrawingBoundariesExplorer<I extends PartitionedLongWritable, V extends CoordinateWritable, E extends Writable, M1 extends LayoutMessage, M2 extends LayoutMessage> extends
	AbstractComputation<I, V, E, M1, M2> {

		protected float[] coords;
		protected V vValue;
		
		@Override
		public void compute(
				Vertex<I, V, E> vertex,
				Iterable<M1> msgs) throws IOException {
			vValue = vertex.getValue();
			coords = vValue.getCoordinates();
			MapWritable myCoordsPackage = new MapWritable();
			myCoordsPackage.put(new LongWritable(vValue.getComponent()), new FloatWritableArray(coords));
			aggregate(maxCoords, myCoordsPackage);
			aggregate(minCoords, myCoordsPackage);
		}
		
		public static class DrawingBoundariesExplorerWithComponentsNo<I extends PartitionedLongWritable, V extends CoordinateWritable, E extends Writable, M1 extends LayoutMessage, M2 extends LayoutMessage> extends
		DrawingBoundariesExplorer<I, V, E, M1, M2>{
			
			@Override
			public void compute(
					Vertex<I, V, E> vertex,
					Iterable<M1> msgs) throws IOException {
				super.compute(vertex, msgs);
				MapWritable information = new MapWritable();
				information.put(new LongWritable(vValue.getComponent()), 
						new IntWritable((int)1 + vertex.getValue().getOneDegreeVerticesQuantity()));
				aggregate(componentNoOfNodes, information);
				aggregate(componentNumber, new LongWritable(vValue.getComponent()));
				}
		}
	}

	/**
	 * This computation applies a previously computed transformation stored into an aggregator (scaling+translation) to components' vertices.
	 * 
	 * @author Alessio Arleo
	 *
	 */
	public static class DrawingScaler <I extends PartitionedLongWritable, V extends CoordinateWritable, E extends Writable, M1 extends LayoutMessage, M2 extends LayoutMessage> extends
	AbstractComputation<I, V, E, M1, M2>{
		
		MapWritable scaleFactors;
		MapWritable minCoordinateMap;

		@Override
		public void preSuperstep() {
			super.preSuperstep();
			scaleFactors = getAggregatedValue(scaleFactorAgg);
			minCoordinateMap = getAggregatedValue(minCoords);
		}

		@Override
		public void compute(
				Vertex<I, V, E> vertex,
				Iterable<M1> msgs) throws IOException {
			V vValue = vertex.getValue();
			float[] coords = vValue.getCoordinates();
			float[] factors = ((FloatWritableArray)scaleFactors.get(new LongWritable(vValue.getComponent()))).get();
			float[] minCoords = ((FloatWritableArray)minCoordinateMap.get(new LongWritable(vValue.getComponent()))).get();			
			vValue.setCoordinates((coords[0] - minCoords[0])*factors[0], (coords[1] - minCoords[1])*factors[1]);
			}
	}
	
	/**
	 * Given the scaling and traslating data computed to arrange the connected components, this computation applies them to each vertex.
	 * 
	 * @author Alessio Arleo
	 *
	 */
	public static class LayoutCCs <I extends PartitionedLongWritable, V extends CoordinateWritable, E extends Writable, M1 extends LayoutMessage, M2 extends LayoutMessage> extends
	AbstractComputation<I, V, E, M1, M2>{
		
		MapWritable offsets;
		
		float componentPadding;
		
		@Override
		public void compute(
				Vertex<I, V, E> vertex,
				Iterable<M1> msgs) throws IOException {
				V vValue = vertex.getValue();
				float[] coords = vValue.getCoordinates();
				float[] ccOffset = ((FloatWritableArray)offsets.get(new LongWritable(vValue.getComponent()))).get();
				vValue.setCoordinates(((coords[0] + ccOffset[0])*ccOffset[2]) + ccOffset[3], ((coords[1] + ccOffset[1])*ccOffset[2]) + ccOffset[4]);
		}
	
		@Override
		public void preSuperstep() {
			offsets = getAggregatedValue(offsetsAggregator);
		}
		
	}

}