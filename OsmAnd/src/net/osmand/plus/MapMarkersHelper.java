package net.osmand.plus;

import android.content.Context;
import android.support.annotation.Nullable;

import net.osmand.data.LatLon;
import net.osmand.data.LocationPoint;
import net.osmand.data.PointDescription;
import net.osmand.util.Algorithms;

import java.util.ArrayList;
import java.util.List;

public class MapMarkersHelper {
	public static final int MAP_MARKERS_COLORS_COUNT = 7;

	private List<MapMarker> mapMarkers = new ArrayList<>();
	private List<MapMarker> mapMarkersHistory = new ArrayList<>();
	private OsmandSettings settings;
	private List<MapMarkerChangedListener> listeners = new ArrayList<>();
	private OsmandApplication ctx;
	private boolean startFromMyLocation;

	public interface MapMarkerChangedListener {
		void onMapMarkerChanged(MapMarker mapMarker);

		void onMapMarkersChanged();
	}

	public static class MapMarker implements LocationPoint {
		public LatLon point;
		private PointDescription pointDescription;
		public int colorIndex;
		public int index;
		public boolean history;
		public boolean selected;
		public int dist;
		public long creationDate;

		public MapMarker(LatLon point, PointDescription name, int colorIndex,
						 boolean selected, long creationDate, int index) {
			this.point = point;
			this.pointDescription = name;
			this.colorIndex = colorIndex;
			this.selected = selected;
			this.creationDate = creationDate;
			this.index = index;
		}

		public PointDescription getPointDescription(Context ctx) {
			return new PointDescription(PointDescription.POINT_TYPE_MAP_MARKER, ctx.getString(R.string.map_marker),
					getOnlyName());
		}

		public String getName(Context ctx) {
			String name;
			PointDescription pd = getPointDescription(ctx);
			if (Algorithms.isEmpty(pd.getName())) {
				name = pd.getTypeName();
			} else {
				name = pd.getName();
			}
			return name;
		}

		public PointDescription getOriginalPointDescription() {
			return pointDescription;
		}

		public String getOnlyName() {
			return pointDescription == null ? "" : pointDescription.getName();
		}

		public double getLatitude() {
			return point.getLatitude();
		}

		public double getLongitude() {
			return point.getLongitude();
		}

		@Override
		public int getColor() {
			return 0;
		}

		@Override
		public boolean isVisible() {
			return false;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			MapMarker mapMarker = (MapMarker) o;

			if (colorIndex != mapMarker.colorIndex) return false;
			return point.equals(mapMarker.point);

		}

		@Override
		public int hashCode() {
			int result = point.hashCode();
			result = 31 * result + colorIndex;
			return result;
		}

		public static int getColorId(int colorIndex) {
			int colorId;
			switch (colorIndex) {
				case 0:
					colorId = R.color.marker_blue;
					break;
				case 1:
					colorId = R.color.marker_green;
					break;
				case 2:
					colorId = R.color.marker_orange;
					break;
				case 3:
					colorId = R.color.marker_red;
					break;
				case 4:
					colorId = R.color.marker_yellow;
					break;
				case 5:
					colorId = R.color.marker_teal;
					break;
				case 6:
					colorId = R.color.marker_purple;
					break;
				default:
					colorId = R.color.marker_blue;
			}
			return colorId;
		}
	}

	public MapMarkersHelper(OsmandApplication ctx) {
		this.ctx = ctx;
		settings = ctx.getSettings();
		startFromMyLocation = settings.ROUTE_MAP_MARKERS_START_MY_LOC.get();
		readFromSettings();
	}

	public boolean isStartFromMyLocation() {
		return startFromMyLocation;
	}

	public void setStartFromMyLocation(boolean startFromMyLocation) {
		this.startFromMyLocation = startFromMyLocation;
		settings.ROUTE_MAP_MARKERS_START_MY_LOC.set(startFromMyLocation);
	}

	public void lookupAddressAll() {
		for (MapMarker mapMarker : mapMarkers) {
			lookupAddress(mapMarker, false);
		}
		for (MapMarker mapMarker : mapMarkersHistory) {
			lookupAddress(mapMarker, true);
		}
	}

	private void readFromSettings() {
		mapMarkers.clear();
		mapMarkersHistory.clear();
		List<LatLon> ips = settings.getMapMarkersPoints();
		List<String> desc = settings.getMapMarkersPointDescriptions(ips.size());
		List<Integer> colors = settings.getMapMarkersColors(ips.size());
		List<Boolean> selections = settings.getMapMarkersSelections(ips.size());
		List<Long> creationDates = settings.getMapMarkersCreationDates(ips.size());
		int colorIndex = 0;
		for (int i = 0; i < ips.size(); i++) {
			if (colors.size() > i) {
				colorIndex = colors.get(i);
			}
			MapMarker mapMarker = new MapMarker(ips.get(i),
					PointDescription.deserializeFromString(desc.get(i), ips.get(i)), colorIndex,
					selections.get(i), creationDates.get(i), i);
			mapMarkers.add(mapMarker);
		}

		ips = settings.getMapMarkersHistoryPoints();
		desc = settings.getMapMarkersHistoryPointDescriptions(ips.size());
		colors = settings.getMapMarkersHistoryColors(ips.size());
		creationDates = settings.getMapMarkersHistoryCreationDates(ips.size());
		for (int i = 0; i < ips.size(); i++) {
			if (colors.size() > i) {
				colorIndex = colors.get(i);
			}
			MapMarker mapMarker = new MapMarker(ips.get(i),
					PointDescription.deserializeFromString(desc.get(i), ips.get(i)),
					colorIndex, false, creationDates.get(i), i);
			mapMarker.history = true;
			mapMarkersHistory.add(mapMarker);
		}

		if (!ctx.isApplicationInitializing()) {
			lookupAddressAll();
		}
	}

	private void lookupAddress(final MapMarker mapMarker, final boolean history) {
		if (mapMarker != null && mapMarker.pointDescription.isSearchingAddress(ctx)) {
			cancelPointAddressRequests(mapMarker.point);
			GeocodingLookupService.AddressLookupRequest lookupRequest = new GeocodingLookupService.AddressLookupRequest(mapMarker.point, new GeocodingLookupService.OnAddressLookupResult() {
				@Override
				public void geocodingDone(String address) {
					if (Algorithms.isEmpty(address)) {
						mapMarker.pointDescription.setName(PointDescription.getAddressNotFoundStr(ctx));
					} else {
						mapMarker.pointDescription.setName(address);
					}
					if (history) {
						settings.updateMapMarkerHistory(mapMarker.point.getLatitude(), mapMarker.point.getLongitude(),
								mapMarker.pointDescription, mapMarker.colorIndex, mapMarker.creationDate);
					} else {
						settings.updateMapMarker(mapMarker.point.getLatitude(), mapMarker.point.getLongitude(),
								mapMarker.pointDescription, mapMarker.colorIndex, mapMarker.selected, mapMarker.creationDate);
					}
					updateMarker(mapMarker);
				}
			}, null);
			ctx.getGeocodingLookupService().lookupAddress(lookupRequest);
		}
	}

	public void removeMapMarker(int index) {
		settings.deleteMapMarker(index);
		MapMarker mapMarker = mapMarkers.remove(index);
		cancelPointAddressRequests(mapMarker.point);
		int ind = 0;
		for (MapMarker marker : mapMarkers) {
			marker.index = ind++;
		}
		refresh();
	}

	public List<MapMarker> getMapMarkers() {
		return mapMarkers;
	}

	public MapMarker getFirstMapMarker() {
		if (mapMarkers.size() > 0) {
			return mapMarkers.get(0);
		} else {
			return null;
		}
	}

	public List<MapMarker> getMapMarkersHistory() {
		return mapMarkersHistory;
	}

	public List<MapMarker> getSelectedMarkers() {
		List<MapMarker> list = new ArrayList<>();
		for (MapMarker m : this.mapMarkers) {
			if (m.selected) {
				list.add(m);
			}
		}
		return list;
	}

	public List<LatLon> getActiveMarkersLatLon() {
		List<LatLon> list = new ArrayList<>();
		for (MapMarker m : this.mapMarkers) {
			list.add(m.point);
		}
		return list;
	}

	public List<LatLon> getSelectedMarkersLatLon() {
		List<LatLon> list = new ArrayList<>();
		for (MapMarker m : this.mapMarkers) {
			if (m.selected) {
				list.add(m.point);
			}
		}
		return list;
	}

	public List<LatLon> getMarkersHistoryLatLon() {
		List<LatLon> list = new ArrayList<>();
		for (MapMarker m : this.mapMarkersHistory) {
			list.add(m.point);
		}
		return list;
	}

	public void reverseActiveMarkersOrder() {
		cancelAddressRequests();

		List<MapMarker> markers = new ArrayList<>(mapMarkers.size());
		for (int i = mapMarkers.size() - 1; i >= 0; i--) {
			MapMarker marker = mapMarkers.get(i);
			markers.add(marker);
		}
		mapMarkers = markers;
		saveMapMarkers(mapMarkers, null);
	}

	public void removeActiveMarkers() {
		cancelAddressRequests();
		for (int i = mapMarkers.size() - 1; i>= 0; i--) {
			MapMarker marker = mapMarkers.get(i);
			addMapMarkerHistory(marker);
		}
		settings.clearActiveMapMarkers();
		readFromSettings();
		refresh();
	}

	public void removeMarkersHistory() {
		cancelAddressRequests();
		settings.clearMapMarkersHistory();
		readFromSettings();
		refresh();
	}

	public void addMapMarker(MapMarker marker, int index) {
		settings.insertMapMarker(marker.getLatitude(), marker.getLongitude(), marker.pointDescription,
				marker.colorIndex, marker.selected, marker.creationDate, index);
		readFromSettings();
	}

	public void addMapMarker(LatLon point, PointDescription historyName) {
		List<LatLon> points = new ArrayList<>(1);
		List<PointDescription> historyNames = new ArrayList<>(1);
		points.add(point);
		historyNames.add(historyName);
		addMapMarkers(points, historyNames);
	}

	public void addMapMarkers(List<LatLon> points, List<PointDescription> historyNames) {
		if (points.size() > 0) {
			int colorIndex = -1;
			double[] latitudes = new double[points.size()];
			double[] longitudes = new double[points.size()];
			List<PointDescription> pointDescriptions = new ArrayList<>();
			int[] colorIndexes = new int[points.size()];
			int[] positions = new int[points.size()];
			boolean[] selections = new boolean[points.size()];
			int[] indexes = new int[points.size()];
			for (int i = 0; i < points.size(); i++) {
				LatLon point = points.get(i);
				PointDescription historyName = historyNames.get(i);
				final PointDescription pointDescription;
				if (historyName == null) {
					pointDescription = new PointDescription(PointDescription.POINT_TYPE_LOCATION, "");
				} else {
					pointDescription = historyName;
				}
				if (pointDescription.isLocation() && Algorithms.isEmpty(pointDescription.getName())) {
					pointDescription.setName(PointDescription.getSearchAddressStr(ctx));
				}
				if (colorIndex == -1) {
					if (mapMarkers.size() > 0) {
						colorIndex = (mapMarkers.get(0).colorIndex + 1) % MAP_MARKERS_COLORS_COUNT;
					} else {
						colorIndex = 0;
					}
				} else {
					colorIndex = (colorIndex + 1) % MAP_MARKERS_COLORS_COUNT;
				}

				latitudes[i] = point.getLatitude();
				longitudes[i] = point.getLongitude();
				pointDescriptions.add(pointDescription);
				colorIndexes[i] = colorIndex;
				positions[i] = -1 - i;
				selections[i] = false;
				indexes[i] = 0;
			}
			/* adding map marker to second topbar's row
			if (sortedMapMarkers.size() > 0) {
				MapMarker firstMarker = sortedMapMarkers.get(0);
				settings.updateMapMarker(firstMarker.getLatitude(), firstMarker.getLongitude(),
						firstMarker.pointDescription, firstMarker.colorIndex, -points.size(), firstMarker.selected);
			}
			*/
			settings.insertMapMarkers(latitudes, longitudes, pointDescriptions, colorIndexes, positions,
					selections, indexes);
			readFromSettings();
		}
	}

	public void updateMapMarker(MapMarker marker, boolean refresh) {
		if (marker != null) {
			settings.updateMapMarker(marker.getLatitude(), marker.getLongitude(),
					marker.pointDescription, marker.colorIndex, marker.selected, marker.creationDate);
			if (refresh) {
				readFromSettings();
				refresh();
			}
		}
	}

	public void moveMapMarker(@Nullable MapMarker marker, LatLon latLon) {
		if (marker != null) {
			settings.moveMapMarker(new LatLon(marker.getLatitude(), marker.getLongitude()), latLon,
					marker.pointDescription, marker.colorIndex, marker.selected, marker.creationDate);
			marker.point = new LatLon(latLon.getLatitude(), latLon.getLongitude());
			readFromSettings();
			refresh();
		}
	}

	public void removeMapMarker(MapMarker marker) {
		if (marker != null) {
			settings.deleteMapMarker(marker.index);
			readFromSettings();
			refresh();
		}
	}

	public void addMapMarkerHistory(MapMarker marker) {
		if (marker != null) {
			settings.insertMapMarkerHistory(marker.getLatitude(), marker.getLongitude(),
					marker.pointDescription, marker.colorIndex, marker.creationDate, 0);
			readFromSettings();
			refresh();
		}
	}

	public void removeMapMarkerHistory(MapMarker marker) {
		if (marker != null) {
			settings.deleteMapMarkerHistory(marker.index);
			readFromSettings();
			refresh();
		}
	}

	public void saveMapMarkers(List<MapMarker> markers, List<MapMarker> markersHistory) {
		if (markers != null) {
			List<LatLon> ls = new ArrayList<>(markers.size());
			List<String> names = new ArrayList<>(markers.size());
			List<Integer> colors = new ArrayList<>(markers.size());
			List<Boolean> selections = new ArrayList<>(markers.size());
			List<Long> creationDates = new ArrayList<>(markers.size());
			for (MapMarker marker : markers) {
				ls.add(marker.point);
				names.add(PointDescription.serializeToString(marker.pointDescription));
				colors.add(marker.colorIndex);
				selections.add(marker.selected);
				creationDates.add(marker.creationDate);
			}
			settings.saveMapMarkers(ls, names, colors, selections, creationDates);
		}

		if (markersHistory != null) {
			List<LatLon> ls = new ArrayList<>(markersHistory.size());
			List<String> names = new ArrayList<>(markersHistory.size());
			List<Integer> colors = new ArrayList<>(markersHistory.size());
			List<Long> creationDates = new ArrayList<>(markersHistory.size());
			for (MapMarker marker : markersHistory) {
				ls.add(marker.point);
				names.add(PointDescription.serializeToString(marker.pointDescription));
				colors.add(marker.colorIndex);
				creationDates.add(marker.creationDate);
			}
			settings.saveMapMarkersHistory(ls, names, colors, creationDates);
		}

		if (markers != null || markersHistory != null) {
			readFromSettings();
			refresh();
		}
	}

	public void addListener(MapMarkerChangedListener l) {
		if (!listeners.contains(l)) {
			listeners.add(l);
		}
	}

	public void removeListener(MapMarkerChangedListener l) {
		listeners.remove(l);
	}

	private void updateMarker(MapMarker marker) {
		for (MapMarkerChangedListener l : listeners) {
			l.onMapMarkerChanged(marker);
		}
	}

	private void updateMarkers() {
		for (MapMarkerChangedListener l : listeners) {
			l.onMapMarkersChanged();
		}
	}

	public void refresh() {
		updateMarkers();
	}

	private void cancelAddressRequests() {
		List<LatLon> list = getActiveMarkersLatLon();
		for (LatLon latLon : list) {
			cancelPointAddressRequests(latLon);
		}
		list = getMarkersHistoryLatLon();
		for (LatLon latLon : list) {
			cancelPointAddressRequests(latLon);
		}
	}

	private void cancelPointAddressRequests(LatLon latLon) {
		if (latLon != null) {
			ctx.getGeocodingLookupService().cancel(latLon);
		}
	}
}
