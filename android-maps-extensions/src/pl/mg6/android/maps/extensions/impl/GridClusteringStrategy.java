/*
 * Copyright (C) 2013 Maciej G�rski
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package pl.mg6.android.maps.extensions.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.util.SparseArray;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

class GridClusteringStrategy implements ClusteringStrategy {

	private GoogleMap provider;
	private Map<DelegatingMarker, ClusterMarker> markers;
	private double clusterSize;

	private SparseArray<ClusterMarker> clusters;

	public GridClusteringStrategy(GoogleMap provider, List<DelegatingMarker> markers) {
		this.provider = provider;
		this.markers = new HashMap<DelegatingMarker, ClusterMarker>();
		for (DelegatingMarker m : markers) {
			this.markers.put(m, null);
		}
		this.clusterSize = calculateClusterSize(provider.getCameraPosition().zoom);
		recalculate();
	}

	@Override
	public void cleanup() {
		if (clusters != null) {
			for (int i = 0; i < clusters.size(); i++) {
				ClusterMarker cluster = clusters.valueAt(i);
				cluster.cleanup();
			}
			clusters = null;
		}
		for (DelegatingMarker marker : markers.keySet()) {
			if (marker.isVisible()) {
				marker.changeVisible(true);
			}
		}
	}

	@Override
	public void onZoomChange(float zoom) {
		double clusterSize = calculateClusterSize(zoom);
		if (this.clusterSize != clusterSize) {
			this.clusterSize = clusterSize;
			recalculate();
		}
	}

	@Override
	public void onAdd(DelegatingMarker marker) {
		LatLng position = marker.getPosition();
		int clusterId = calculateClusterId(position);
		ClusterMarker cluster = clusters.get(clusterId);
		if (cluster == null) {
			cluster = new ClusterMarker(clusterId, provider.addMarker(new MarkerOptions().position(position).visible(false)
					.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN))));
			clusters.put(clusterId, cluster);
		}
		cluster.add(marker);
		markers.put(marker, cluster);
		if (marker.isVisible()) {
			cluster.fixVisibilityAndPosition();
		}
	}

	@Override
	public void onRemove(DelegatingMarker marker) {
		ClusterMarker cluster = markers.remove(marker);
		cluster.remove(marker);
		if (cluster.getCount() == 0) {
			cluster.cleanup();
			clusters.remove(cluster.getClusterId());
		} else {
			cluster.fixVisibilityAndPosition();
		}
	}

	@Override
	public void onPositionChange(DelegatingMarker marker) {
		ClusterMarker cluster = markers.get(marker);
		int clusterId = cluster.getClusterId();
		LatLng position = marker.getPosition();
		int newClusterId = calculateClusterId(position);
		if (newClusterId == clusterId) {
			if (marker.isVisible()) {
				cluster.fixVisibilityAndPosition();
			}
		} else {
			cluster.remove(marker);
			if (cluster.getCount() == 0) {
				cluster.cleanup();
				clusters.remove(clusterId);
			} else {
				cluster.fixVisibilityAndPosition();
			}
			ClusterMarker newCluster = clusters.get(newClusterId);
			if (newCluster == null) {
				newCluster = new ClusterMarker(newClusterId, provider.addMarker(new MarkerOptions().position(position).visible(false)
						.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN))));
				clusters.put(newClusterId, newCluster);
			}
			newCluster.add(marker);
			markers.put(marker, newCluster);
			if (marker.isVisible()) {
				cluster.fixVisibilityAndPosition();
			}
		}
	}

	@Override
	public void onVisibilityChangeRequest(DelegatingMarker marker, boolean visible) {
		ClusterMarker cluster = markers.get(marker);
		cluster.fixVisibilityAndPosition();
	}

	private void recalculate() {
		if (clusters != null) {
			for (int i = 0; i < clusters.size(); i++) {
				ClusterMarker cluster = clusters.valueAt(i);
				cluster.cleanup();
			}
			clusters = null;
			for (DelegatingMarker marker : markers.keySet()) {
				markers.put(marker, null);
			}
		}
		if (clusterSize == 0.0) {
			for (DelegatingMarker marker : markers.keySet()) {
				if (marker.isVisible()) {
					marker.changeVisible(true);
				}
			}
		} else {
			clusters = new SparseArray<ClusterMarker>();
			for (DelegatingMarker marker : markers.keySet()) {
				LatLng position = marker.getPosition();
				int clusterId = calculateClusterId(position);
				ClusterMarker cluster = clusters.get(clusterId);
				if (cluster == null) {
					cluster = new ClusterMarker(clusterId, provider.addMarker(new MarkerOptions().position(position).visible(false)
							.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN))));
					clusters.put(clusterId, cluster);
				}
				cluster.add(marker);
				markers.put(marker, cluster);
			}
			for (int i = 0; i < clusters.size(); i++) {
				ClusterMarker cluster = clusters.valueAt(i);
				cluster.fixVisibilityAndPosition();
			}
		}
	}

	private int calculateClusterId(LatLng position) {
		int y = (int) ((position.latitude + 180.0) / clusterSize);
		int x = (int) ((position.longitude + 90.0) / clusterSize);
		return (y << 16) + x;
	}

	private double calculateClusterSize(float zoom) {
		return (1 << ((int) (23.5f - zoom))) / 100000.0;
	}
}