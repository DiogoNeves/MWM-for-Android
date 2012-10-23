package org.metawatch.manager;

import java.util.List;

import org.metawatch.manager.MetaWatchService.Preferences;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

/**
 * Creates am updater with one listener. This is a simpler interface than the
 * Android's APIs that should only do what we need. We're trading flexibility
 * with simplicity (as we don't need much more than this).
 * 
 * It'll detect changes in the providers and use the finest one available. Keep
 * in mind that, as soon as you turn GPS on, it'll stop using any other provider
 * even while searching for satellites. You can always
 * getLastBestKnownLocation() whenever you need a quick fix.
 * 
 * It uses the Fluent pattern so you can write it like: </br><code>
 * LocationUpdater updater = new LocationUpdater()</br>
 * &nbsp;&nbsp;&nbsp;&nbsp;.setInterval(30 * 60 * 1000)</br>
 * &nbsp;&nbsp;&nbsp;&nbsp;.setDistance(100)</br>
 * &nbsp;&nbsp;&nbsp;&nbsp;.setCriteria(criteria)</br>
 * &nbsp;&nbsp;&nbsp;&nbsp;.setListener(listener)</br>
 * &nbsp;&nbsp;&nbsp;&nbsp;.start();</br>
 * </code> or</br><code>
 * LocationUpdater updater = new LocationUpdater().start();</br>
 * </code> or even normally as</br><code>
 * LocationUpdater updater = new LocationUpdater();</br>
 * updater.start();</br>
 * </code> and it'll work just fine ;)
 */
public class LocationUpdater extends ContextWrapper {

	public static final String TAG = "LocationUpdate";

	public static boolean SUPPORTS_GINGERBREAD = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.GINGERBREAD;

	public static final String KEY_LOCATION_CHANGED = "LOCATION_CHANGED";

	public interface ISimpleLocationListener {
		public void onLocationChanged(String provider, Location location);
	}

	private LocationManager locationManager;
	private PendingIntent intent;

	private Criteria criteria;
	private long updateInterval;
	private float updateDistance;

	private boolean started;

	public LocationUpdater(Context context) {
		super(context);

		// Get Android's location manager
		locationManager = (LocationManager) context
				.getSystemService(Context.LOCATION_SERVICE);

		Intent activeIntent = new Intent(context, LocationReceiver.class);
		intent = PendingIntent.getBroadcast(context, 0, activeIntent,
				PendingIntent.FLAG_UPDATE_CURRENT);

		criteria = new Criteria();
		criteria.setAccuracy(Criteria.ACCURACY_FINE);

		updateInterval = 1 * 60 * 1000;
		updateDistance = 100.0f;

		started = false;
	}

	public long getInterval() {
		return updateInterval;
	}

	public LocationUpdater setInterval(long interval) {
		updateInterval = interval;
		return this;
	}

	public float getDistance() {
		return updateDistance;
	}

	public LocationUpdater setDistance(float distance) {
		updateDistance = distance;
		return this;
	}

	public Criteria getCriteria() {
		return criteria;
	}

	public LocationUpdater setCriteria(Criteria criteria) {
		this.criteria = criteria;
		return this;
	}

	public Location getLastBestKnownLocation() {
		Location bestResult = null;
		float bestAccuracy = Float.MAX_VALUE;
		long bestTime = Long.MIN_VALUE;

		// Iterate through all the providers on the system, keeping
		// note of the most accurate result within the acceptable time limit.
		// If no result is found within maxTime, return the newest Location.
		List<String> matchingProviders = locationManager.getAllProviders();
		for (String provider : matchingProviders) {
			Location location = locationManager.getLastKnownLocation(provider);
			if (location != null) {
				float accuracy = location.getAccuracy();
				long time = location.getTime();

				if ((time > getInterval() && accuracy < bestAccuracy)) {
					bestResult = location;
					bestAccuracy = accuracy;
					bestTime = time;
				} else if (time < getInterval()
						&& bestAccuracy == Float.MAX_VALUE && time > bestTime) {
					bestResult = location;
					bestTime = time;
				}
			}
		}

		return bestResult;
	}

	/**
	 * Starts updating the location. The first update might take a while
	 * (interval) to be triggered. If you need a quick fix, please look at
	 * calling getLastBestKnownLocation().
	 * 
	 * After calling start, if you change any options (other than the listener)
	 * you'll have to call stop() and start() again.
	 * 
	 * @return this
	 */
	public LocationUpdater start() {
		assert (locationManager != null);
		assert (started == false);

		Log.d(TAG, "Starting location updates");

		started = true;
		if (SUPPORTS_GINGERBREAD)
			startNewApi();
		else
			startOldApi();

		return this;
	}

	@TargetApi(9)
	private void startNewApi() {
		if (Preferences.logging) Log.d(TAG, "Using new location updates");
		
		locationManager.requestLocationUpdates(getInterval(), getDistance(),
				getCriteria(), intent);
	}

	private void startOldApi() {
		if (Preferences.logging) Log.d(TAG, "Using old location updates");
		
		// TODO Support multiple providers on platforms older than Gingerbread
		
		locationManager.requestLocationUpdates(
				LocationManager.NETWORK_PROVIDER, getInterval(), getDistance(),
				intent);
	}

	public void stop() {
		assert (started == true);
		assert (locationManager != null);

		locationManager.removeUpdates(intent);
		started = false;

		Log.d(TAG, "Stopped location updates");
	}

	public static class LocationReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.hasExtra(LocationManager.KEY_LOCATION_CHANGED)) {
				Location location = (Location) intent.getExtras().get(
						LocationManager.KEY_LOCATION_CHANGED);
				
				if (Preferences.logging) Log.d(TAG, "redirecting location update");
				
				Intent sendIntent = new Intent(
						"org.metawatch.manager.LOCATION_CHANGE");
				sendIntent.putExtra(KEY_LOCATION_CHANGED, location);
				context.sendBroadcast(sendIntent);
			}
		}

	}
}
