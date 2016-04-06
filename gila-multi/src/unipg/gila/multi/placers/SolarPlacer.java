/**
 * 
 */
package unipg.gila.multi.placers;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

import org.apache.giraph.comm.WorkerClientRequestProcessor;
import org.apache.giraph.edge.Edge;
import org.apache.giraph.graph.GraphState;
import org.apache.giraph.graph.GraphTaskManager;
import org.apache.giraph.graph.Vertex;
import org.apache.giraph.worker.WorkerContext;
import org.apache.giraph.worker.WorkerGlobalCommUsage;
import org.apache.hadoop.io.FloatWritable;
import org.apache.hadoop.io.Writable;
import org.apache.log4j.Logger;

import unipg.gila.common.datastructures.messagetypes.LayoutMessage;
import unipg.gila.multi.MultiScaleComputation;
import unipg.gila.multi.coarseners.SolarMergerRoutine;
import unipg.gila.multi.common.AstralBodyCoordinateWritable;
import unipg.gila.multi.common.LayeredPartitionedLongWritable;
import unipg.gila.multi.common.PathWritable;
import unipg.gila.multi.common.PathWritableSet;

/**
 * @author Alessio Arleo
 *
 */
public class SolarPlacer extends MultiScaleComputation<AstralBodyCoordinateWritable, LayoutMessage, LayoutMessage> {

	//LOGGER
	protected Logger log = Logger.getLogger(SolarPlacer.class);
	
	protected float defaultLength;
	
	/* (non-Javadoc)
	 * @see unipg.gila.multi.MultiScaleComputation#vertexInLayerComputation(org.apache.giraph.graph.Vertex, java.lang.Iterable)
	 */
	@Override
	protected void vertexInLayerComputation(
			Vertex<LayeredPartitionedLongWritable, AstralBodyCoordinateWritable, FloatWritable> vertex,
			Iterable<LayoutMessage> msgs) throws IOException {
		AstralBodyCoordinateWritable value = vertex.getValue();
		
		if(!value.isSun() && value.getLowerLevelWeight() == 0)
			return;
		
		log.info("I'm "+ vertex.getId() + " and these are my neighbors:\n");
		log.info(value.neighborSystemsStateToString());
		log.info("\nThese are my upward edges:\n");
		
		Iterator<Edge<LayeredPartitionedLongWritable, FloatWritable>> edges = vertex.getEdges().iterator();
		while(edges.hasNext()){
			Edge<LayeredPartitionedLongWritable, FloatWritable> next = edges.next();
			if(next.getTargetVertexId().getLayer() == currentLayer +1)
				log.info(next.getTargetVertexId().getId());
		}
		
		log.info("\nThese are the mesages received:\n");
				
		Iterator<LayoutMessage> itmsgs = msgs.iterator();
		HashMap<Long, float[]> coordsMap = new HashMap<Long, float[]>();
		HashMap<LayeredPartitionedLongWritable, float[]> planetsComputedCoordsMap = new HashMap<LayeredPartitionedLongWritable, float[]>();
		HashMap<LayeredPartitionedLongWritable, float[]> moonsComputedCoordsMap = new HashMap<LayeredPartitionedLongWritable, float[]>();
		
		while(itmsgs.hasNext()){ //Check all messages and gather all the coordinates (including updating mine).
			LayoutMessage msg = itmsgs.next();
			log.info(msg.getPayloadVertex());
			if(msg.getPayloadVertex() == vertex.getId().getId()){
				value.setCoordinates(msg.getValue()[0], msg.getValue()[1]);
			}else
				coordsMap.put(msg.getPayloadVertex(), msg.getValue());			
		}
		
		//ARRANGE PLANETS
		Iterator<Entry<Writable, Writable>> planetsIterator = vertex.getValue().getPlanetsIterator();
		if(planetsIterator != null)
			arrangeBodies(value.getCoordinates(), planetsIterator, planetsComputedCoordsMap, coordsMap);
		
		//ARRANGE MOONS
		Iterator<Entry<Writable, Writable>> moonsIterator = vertex.getValue().getMoonsIterator();
		if(moonsIterator != null)
			arrangeBodies(value.getCoordinates(), moonsIterator, moonsComputedCoordsMap, coordsMap);		
		
		//SEND ALL PACKETS TO PLANETS
		Iterator<Entry<LayeredPartitionedLongWritable, float[]>> finalIteratorOnPlanets = planetsComputedCoordsMap.entrySet().iterator();
		while(finalIteratorOnPlanets.hasNext()){
			Entry<LayeredPartitionedLongWritable, float[]> currentRecipient = finalIteratorOnPlanets.next();
			sendMessage(currentRecipient.getKey().copy(), new LayoutMessage(currentRecipient.getKey().getId(), currentRecipient.getValue()));
		}
		
		//SEND ALL PACKETS TO MOONS
		Iterator<Entry<LayeredPartitionedLongWritable, float[]>> finalIteratorOnMoons = moonsComputedCoordsMap.entrySet().iterator();
		while(finalIteratorOnMoons.hasNext()){
			Entry<LayeredPartitionedLongWritable, float[]> currentRecipient = finalIteratorOnMoons.next();
			sendMessageToAllEdges(vertex, new LayoutMessage(currentRecipient.getKey().getId(), 1, currentRecipient.getValue()));
		}
	}
	
	/**
	 * @param bodiesIterator
	 * @param bodiesMap
	 */
	@SuppressWarnings("unchecked")
	private void arrangeBodies(float[] myCoordinates,
			Iterator<Entry<Writable, Writable>> bodiesIterator,
			HashMap<LayeredPartitionedLongWritable, float[]> bodiesMap, HashMap<Long, float[]> coordsMap) {
		while(bodiesIterator.hasNext()){
			float avgX = 0.0f, avgY = 0.0f;
			Entry<Writable, Writable> current = bodiesIterator.next();
			PathWritableSet deSet = (PathWritableSet) current.getValue();
			Iterator<PathWritable> deSetIterator = (Iterator<PathWritable>) deSet.iterator();
			while(deSetIterator.hasNext()){
				PathWritable currentPath = deSetIterator.next();
				float deltaX = myCoordinates[0] - coordsMap.get(currentPath.getReferencedSun().getId())[0];
				float deltaY = myCoordinates[1] - coordsMap.get(currentPath.getReferencedSun().getId())[1];
				
				avgX += deltaX*(currentPath.getPositionInpath()/(float)currentPath.getPathLength());
				avgY += deltaY*(currentPath.getPositionInpath()/(float)currentPath.getPathLength());

			}
			bodiesMap.put((LayeredPartitionedLongWritable) current.getKey(), new float[]{avgX/deSet.size(), avgY/deSet.size()});
		}		
	}

	/* (non-Javadoc)
	 * @see unipg.gila.multi.MultiScaleComputation#initialize(org.apache.giraph.graph.GraphState, org.apache.giraph.comm.WorkerClientRequestProcessor, org.apache.giraph.graph.GraphTaskManager, org.apache.giraph.worker.WorkerGlobalCommUsage, org.apache.giraph.worker.WorkerContext)
	 */
	@Override
	public void initialize(
			GraphState graphState,
			WorkerClientRequestProcessor<LayeredPartitionedLongWritable, AstralBodyCoordinateWritable, FloatWritable> workerClientRequestProcessor,
			GraphTaskManager<LayeredPartitionedLongWritable, AstralBodyCoordinateWritable, FloatWritable> graphTaskManager,
			WorkerGlobalCommUsage workerGlobalCommUsage,
			WorkerContext workerContext) {
		super.initialize(graphState, workerClientRequestProcessor, graphTaskManager,
				workerGlobalCommUsage, workerContext);
		defaultLength = getConf().getFloat(SolarPlacerRoutine.placerDefaultLengthString, SolarPlacerRoutine.placerDefaultLength);
	}
	
}
