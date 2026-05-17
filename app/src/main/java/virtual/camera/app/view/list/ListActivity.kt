package virtual.camera.app.view.list

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import cbfg.rvadapter.RVAdapter
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItems
import com.ferfalk.simplesearchview.SimpleSearchView
import virtual.camera.app.R
import virtual.camera.app.bean.InstalledAppBean
import virtual.camera.app.databinding.ActivityListBinding
import virtual.camera.app.util.InjectionUtil
import virtual.camera.app.util.inflate
import virtual.camera.app.view.base.BaseActivity
import virtual.camera.camera.MultiPreferences


class ListActivity : BaseActivity() {

    private val viewBinding: ActivityListBinding by inflate()
    private lateinit var mAdapter: RVAdapter<InstalledAppBean>
    private lateinit var viewModel: ListViewModel
    private var appList: List<InstalledAppBean> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)
        initToolbar(viewBinding.toolbarLayout.toolbar, R.string.installed_app, true)

        // تهيئة MultiPreferences
        MultiPreferences.init(this)

        mAdapter = RVAdapter<InstalledAppBean>(this, ListAdapter()).bind(viewBinding.recyclerView)
            .setItemClickListener { _, item, _ ->
                finishWithResult(item.packageName)
            }
        viewBinding.recyclerView.layoutManager = LinearLayoutManager(this)
        initSearchView()
        initViewModel()
    }

    // ✅ عرض نافذة اختيار الوسائط
    private fun showMediaPicker() {
        MaterialDialog(this).show {
            title(text = "اختر مصدر الكاميرا الافتراضية")
            listItems(items = listOf("🎥 فيديو", "🖼️ صورة")) { _, index, _ ->
                when (index) {
                    0 -> pickVideoResult.launch("video/*")
                    1 -> pickImageResult.launch("image/*")
                }
            }
        }
    }

    // ✅ اختيار فيديو وحفظه في MultiPreferences
    private val pickVideoResult =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                // منح صلاحية القراءة الدائمة
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                // حفظ مسار الفيديو
                MultiPreferences.getInstance().setString("camera_source", it.toString())
                MultiPreferences.getInstance().setString("camera_type", "video")
                showSuccessDialog("تم تعيين الفيديو ككاميرا افتراضية ✅")
                finishWithResult(it.toString())
            }
        }

    // ✅ اختيار صورة وحفظها في MultiPreferences
    private val pickImageResult =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                MultiPreferences.getInstance().setString("camera_source", it.toString())
                MultiPreferences.getInstance().setString("camera_type", "image")
                showSuccessDialog("تم تعيين الصورة ككاميرا افتراضية ✅")
                finishWithResult(it.toString())
            }
        }

    private fun showSuccessDialog(message: String) {
        MaterialDialog(this).show {
            title(text = "نجح ✅")
            message(text = message)
            positiveButton(text = "حسناً")
        }
    }

    private fun initSearchView() {
        viewBinding.searchView.setOnQueryTextListener(object :
            SimpleSearchView.OnQueryTextListener {
            override fun onQueryTextChange(newText: String): Boolean {
                filterApp(newText)
                return true
            }
            override fun onQueryTextCleared(): Boolean = true
            override fun onQueryTextSubmit(query: String): Boolean = true
        })
    }

    private fun initViewModel() {
        viewModel = ViewModelProvider(this, InjectionUtil.getListFactory())
            .get(ListViewModel::class.java)
        val onlyShowXp = intent.getBooleanExtra("onlyShowXp", false)
        val userID = intent.getIntExtra("userID", 0)

        if (onlyShowXp) {
            viewModel.getInstalledModules()
            viewBinding.toolbarLayout.toolbar.setTitle(R.string.installed_module)
        } else {
            viewModel.getInstallAppList(userID)
            viewBinding.toolbarLayout.toolbar.setTitle(R.string.installed_app)
        }

        viewModel.loadingLiveData.observe(this) {
            if (it) viewBinding.stateView.showLoading()
            else viewBinding.stateView.showContent()
        }

        viewModel.appsLiveData.observe(this) {
            if (it != null) {
                this.appList = it
                viewBinding.searchView.setQuery("", false)
                filterApp("")
                if (it.isNotEmpty()) {
                    viewBinding.stateView.showContent()
                    viewModel.previewInstalledList()
                } else {
                    viewBinding.stateView.showEmpty()
                }
            }
        }
    }

    private fun filterApp(newText: String) {
        val newList = this.appList.filter {
            it.name.contains(newText, true) or it.packageName.contains(newText, true)
        }
        mAdapter.setItems(newList)
    }

    private fun finishWithResult(source: String) {
        intent.putExtra("source", source)
        setResult(Activity.RESULT_OK, intent)
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        window.peekDecorView()?.run {
            imm.hideSoftInputFromWindow(windowToken, 0)
        }
        finish()
    }

    override fun onBackPressed() {
        if (viewBinding.searchView.isSearchOpen) {
            viewBinding.searchView.closeSearch()
        } else {
            super.onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_list, menu)
        val item = menu!!.findItem(R.id.list_search)
        viewBinding.searchView.setMenuItem(item)
        // ✅ زر اختيار فيديو/صورة
        menu.add(0, 999, 1, "📹 كاميرا")
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 999) {
            showMediaPicker()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onStop() {
        super.onStop()
        viewModel.loadingLiveData.postValue(true)
        viewModel.loadingLiveData.removeObservers(this)
        viewModel.appsLiveData.postValue(null)
        viewModel.appsLiveData.removeObservers(this)
    }

    companion object {
        fun start(context: Context, onlyShowXp: Boolean) {
            val intent = Intent(context, ListActivity::class.java)
            intent.putExtra("onlyShowXp", onlyShowXp)
            context.startActivity(intent)
        }
    }
}
