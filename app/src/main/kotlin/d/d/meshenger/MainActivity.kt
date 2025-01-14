package d.d.meshenger

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import d.d.meshenger.MainService.MainBinder

// the main view with tabs
class MainActivity : BaseActivity(), ServiceConnection {
    internal var binder: MainBinder? = null
    private lateinit var viewPager: ViewPager2

    private fun initToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.apply {
            setNavigationOnClickListener {
                finish()
            }
        }
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(false)
            setDisplayShowTitleEnabled(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(this, "onCreate")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initToolbar()
        permissionToDrawOverlays()

        viewPager = findViewById(R.id.container)
        viewPager.adapter = ViewPagerFragmentAdapter(this)

        bindService(Intent(this, MainService::class.java), this, 0)
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(this)
    }

    private fun isWifiConnected(): Boolean {
        val context = this as Context
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false

            return when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                //activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                else -> false
            }
        } else {
            val connManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            @Suppress("DEPRECATION")
            val mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI) ?: return false
            @Suppress("DEPRECATION")
            return mWifi.isConnected
        }
    }

    private fun showInvalidAddressSettingsWarning() {
        Handler(Looper.getMainLooper()).postDelayed({
            val localBinder = this@MainActivity.binder
            if (localBinder == null) {
                Log.w(this, "binder is null")
                return@postDelayed
            }

            val storedAddresses = localBinder.getSettings().addresses
            val storedIPAddresses = storedAddresses.filter { AddressUtils.isIPAddress(it) || AddressUtils.isMACAddress(it) }
            if (storedAddresses.isNotEmpty() && storedIPAddresses.isEmpty()) {
                // ignore, we only have domains configured
            } else if (storedAddresses.isEmpty()) {
                // no addresses configured at all
                Toast.makeText(this, R.string.warning_no_addresses_configured, Toast.LENGTH_LONG).show()
            } else {
                if (isWifiConnected()) {
                    val systemAddresses = AddressUtils.collectAddresses().map { it.address }
                    if (storedIPAddresses.intersect(systemAddresses.toSet()).isEmpty()) {
                        // none of the configured addresses are used in the system
                        // addresses might have changed!
                        Toast.makeText(this, R.string.warning_no_addresses_found, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }, 700)
    }

    private fun permissionToDrawOverlays() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                requestDrawOverlaysPermissionLauncher.launch(intent)
            }
        }
    }

    var requestDrawOverlaysPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            if (Build.VERSION.SDK_INT >= 23) {
                if (!Settings.canDrawOverlays(this)) {
                    // ADD UI FOR USER TO KNOW THAT UI for SYSTEM_ALERT_WINDOW permission was not granted earlier...
                }
            }
        }
    }

    override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
        Log.d(this, "onServiceConnected")
        binder = iBinder as MainBinder

        val tabLayout = findViewById<TabLayout>(R.id.tabs)
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.title_contacts)
                else -> getString(R.string.title_events)
            }
        }.attach()

        if (!address_warning_shown) {
            showInvalidAddressSettingsWarning()
            address_warning_shown = true
        }

        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent("refresh_contact_list"))
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent("refresh_event_list"))

        // call it here because EventListFragment.onResume is triggered twice
        try {
            binder!!.pingContacts()
        } catch (e: Exception) {
        }
    }

    override fun onServiceDisconnected(componentName: ComponentName) {
        Log.d(this, "onServiceConnected")
        binder = null
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.d(this, "onOptionsItemSelected")
        when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            R.id.action_backup -> {
                startActivity(Intent(this, BackupActivity::class.java))
            }
            R.id.action_about -> {
                startActivity(Intent(this, AboutActivity::class.java))
            }
            R.id.action_exit -> {
                MainService.stop(this)
                finish()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        Log.d(this, "onResume")
        super.onResume()

        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent("refresh_contact_list"))
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent("refresh_event_list"))

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        Log.d(this, "onCreateOptionsMenu")
        menuInflater.inflate(R.menu.menu_main_activity, menu)
        return true
    }

    class ViewPagerFragmentAdapter(fm: FragmentActivity) : FragmentStateAdapter(fm) {
        override fun getItemCount(): Int {
            return 2
        }

        override fun createFragment(position: Int): Fragment {
            if (position == 0) {
                return ContactListFragment()
            } else {
                return EventListFragment()
            }
        }
    }

    companion object {
        private var address_warning_shown = false
    }
}
