package com.safemode.antitheft.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.safemode.antitheft.databinding.ActivityIntruderLogBinding
import com.safemode.antitheft.databinding.ItemIntruderLogBinding
import com.safemode.antitheft.model.IntruderLog
import com.safemode.antitheft.util.PreferenceHelper
import java.io.File

class IntruderLogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIntruderLogBinding
    private lateinit var prefs: PreferenceHelper
    private var adapter: IntruderLogAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIntruderLogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferenceHelper(this)
        setupUI()
        loadLogs()
    }

    private fun setupUI() {
        binding.rvIntruderLogs.layoutManager = LinearLayoutManager(this)

        binding.btnClearAllLogs.setOnClickListener {
            // Delete image files
            val logs = prefs.getIntruderLogs()
            for (log in logs) {
                try {
                    File(log.photoPath).delete()
                } catch (e: Exception) {
                    // ignore
                }
            }
            prefs.clearIntruderLogs()
            loadLogs()
            Toast.makeText(this, "Evidence vault cleared.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadLogs() {
        val logs = prefs.getIntruderLogs()
        if (logs.isEmpty()) {
            binding.rvIntruderLogs.visibility = View.GONE
            binding.layoutEmptyLogs.visibility = View.VISIBLE
        } else {
            binding.rvIntruderLogs.visibility = View.VISIBLE
            binding.layoutEmptyLogs.visibility = View.GONE
            adapter = IntruderLogAdapter(logs)
            binding.rvIntruderLogs.adapter = adapter
        }
    }

    class IntruderLogAdapter(private val list: List<IntruderLog>) :
        RecyclerView.Adapter<IntruderLogAdapter.LogViewHolder>() {

        class LogViewHolder(val binding: ItemIntruderLogBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
            val binding = ItemIntruderLogBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return LogViewHolder(binding)
        }

        override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
            val item = list[position]
            holder.binding.tvLogEventTitle.text = item.reason
            holder.binding.tvLogTimestamp.text = item.timestamp
            holder.binding.tvLogDetails.text = "Battery: ${item.batteryLevel}% | Proof Captured"

            val imgFile = File(item.photoPath)
            if (imgFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
                holder.binding.ivIntruderThumb.setImageBitmap(bitmap)
            }
        }

        override fun getItemCount(): Int = list.size
    }
}
