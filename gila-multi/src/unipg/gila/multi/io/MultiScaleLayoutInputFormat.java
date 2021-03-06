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
package unipg.gila.multi.io;

import java.io.IOException;
import java.util.List;

import org.apache.giraph.edge.Edge;
import org.apache.giraph.edge.EdgeFactory;
import org.apache.giraph.graph.Vertex;
import org.apache.giraph.io.formats.TextVertexInputFormat;
import org.apache.hadoop.io.FloatWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.json.JSONArray;
import org.json.JSONException;

import unipg.gila.common.coordinatewritables.AstralBodyCoordinateWritable;
import unipg.gila.common.coordinatewritables.CoordinateWritable;
import unipg.gila.common.datastructures.PartitionedLongWritable;
import unipg.gila.common.multi.LayeredPartitionedLongWritable;

import com.google.common.collect.Lists;

/**
 * This class is used to load data for the layout computation. It is a JSON format formed as follows:
 * 
 * [id, partition, connected component, x coordinate, y coordinate, n. of one degree vertices neighbors,[ [neighbor vertex Id, neighbor vertex partition] {,[neighbor vertex Id, neighbor vertex partition]}* ]]
 * 
 * It fits SpinnerVertexOutputFormat.
 * 
 * @author Alessio Arleo
 *
 */
public class MultiScaleLayoutInputFormat extends
TextVertexInputFormat<LayeredPartitionedLongWritable, AstralBodyCoordinateWritable, IntWritable> {
	
	@Override
	public TextVertexInputFormat<LayeredPartitionedLongWritable, AstralBodyCoordinateWritable, IntWritable>.TextVertexReader createVertexReader(
			InputSplit in, TaskAttemptContext out) throws IOException {
		return new JSONPartitionedLongArrayFloatVertexReader();
	}

	protected class JSONPartitionedLongArrayFloatVertexReader extends
	TextVertexReaderFromEachLineProcessedHandlingExceptions<JSONArray, JSONException> {

		@Override
		protected JSONArray preprocessLine(Text line) throws JSONException {
			return new JSONArray(line.toString());
		}

		@Override
		protected LayeredPartitionedLongWritable getId(JSONArray jsonVertex) throws JSONException,
		IOException {
			return new LayeredPartitionedLongWritable(jsonVertex.getInt(2) + "_" + jsonVertex.getLong(0));
		}

		@Override
		protected AstralBodyCoordinateWritable getValue(JSONArray jsonVertex) throws
		JSONException, IOException {
				return new AstralBodyCoordinateWritable(new Double(jsonVertex.getDouble(3)).floatValue(), new Double(jsonVertex.getDouble(4)).floatValue(), jsonVertex.getJSONArray(5), jsonVertex.getInt(1));
		}

		protected Vertex<PartitionedLongWritable, CoordinateWritable, FloatWritable> handleException(Text line, JSONArray jsonVertex,
				Exception e) {
			return null;
		}

		protected Iterable<Edge<LayeredPartitionedLongWritable, IntWritable>> getEdges(JSONArray jsonVertex) throws JSONException, IOException {
			JSONArray jsonEdgeArray = jsonVertex.getJSONArray(6);
			List<Edge<LayeredPartitionedLongWritable, IntWritable>> edges =	Lists.newArrayList();
			int i;
			for (i = 0; i < jsonEdgeArray.length(); ++i) {
				JSONArray jsonEdge = jsonEdgeArray.getJSONArray(i);
				edges.add(EdgeFactory.create(new LayeredPartitionedLongWritable(jsonEdge.getInt(1) + "_" + jsonEdge.getLong(0)),
						new IntWritable(1)));
			}
			return edges;
		}

	}

}
