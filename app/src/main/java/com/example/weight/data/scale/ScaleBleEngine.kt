package com.example.weight.data.scale

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import com.example.weight.data.LocalStorageData
import com.example.weight.data.record.BodyComposition
import com.example.weight.data.record.BodyCompositionJson
import com.example.weight.data.record.Record
import com.example.weight.data.record.RecordDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * icomon 体脂秤蓝牙引擎：扫描 → 连接 → 订阅 FFB2 通知 → 解析体重 → 去重入库。
 *
 * 整个流程由 [startSession] 驱动，状态经 [state] 暴露给设置页；
 * 稳定体重写入 Record 表后通过 [onRecordInserted] 回调通知（SnackBar/小组件刷新）。
 * 所有帧原样进 [rawLog]，未知格式留作协议逆向的现场证据。
 */
@SuppressLint("MissingPermission")
class ScaleBleEngine(
    private val context: Context,
    private val appScope: CoroutineScope,
    private val recordDao: RecordDao,
) {
    /** 会话状态机；UI 只读 */
    sealed interface State {
        /** 空闲，未在称重会话中 */
        data object Idle : State

        /** 正在扫描 icomon 广播 */
        data class Scanning(val secondsLeft: Int) : State

        /** 已锁定信号，正在建立 GATT 连接与订阅 */
        data object Connecting : State

        /** 已连接，等待上秤出数 */
        data class Ready(val deviceName: String) : State

        /** 收到测量中体重（实时值） */
        data class Measuring(val weightKg: Double) : State

        /** 体重已稳定，等待体脂结果帧（阻抗）或超时收尾 */
        data class Stabilized(val weightKg: Double) : State

        /** 本轮称重完成，已入库 */
        data class Done(
            val weightKg: Double,
            val fatRatio: Double? = null,
            val impedanceOhm: Double? = null,
        ) : State

        /** 失败/超时，message 为中文用户可读原因 */
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** 调试日志：每条为"方向+十六进制"，未知协议分析用 */
    private val _rawLog = MutableStateFlow<List<String>>(emptyList())
    val rawLog: StateFlow<List<String>> = _rawLog.asStateFlow()

    /** 稳定体重入库后的回调（UI 层刷新小组件等） */
    var onRecordInserted: ((Double) -> Unit)? = null

    /**
     * 最近一次称重的完整成分（含全指标）。弹窗模式（autoInsert=false）下由调用方
     * 在用户确认保存时取用；startSession 时清空。
     */
    var lastComposition: BodyComposition? = null
        private set

    /**
     * 弹窗读秤模式：完成时只解析并停在 Done，不自动写库——由记录弹窗在用户点保存时
     * 连同体重一起入库；设置页称重卡仍用默认的自动入库。
     */
    private var autoInsertOnDone = true

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? get() = bluetoothManager.adapter

    private var scanTimeoutJob: Job? = null
    private var gatt: BluetoothGatt? = null
    private var sessionActive = false

    // ---- GATT 常量（icomon 家族通用外形）----
    private val serviceUuid: UUID = UUID.fromString("0000ffb0-0000-1000-8000-00805f9b34fb")
    private val writeCharUuid: UUID = UUID.fromString("0000ffb1-0000-1000-8000-00805f9b34fb")
    private val notifyCharUuid: UUID = UUID.fromString("0000ffb2-0000-1000-8000-00805f9b34fb")
    private val cccDescriptorUuid: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /**
     * 开始一次称重会话：扫描最长 60 秒，发现秤后连接并等待数据。
     * [autoInsert] false 时完成后不写库（记录弹窗读秤用，由调用方保存）。
     * 重复调用时先清理上一会话。
     */
    fun startSession(autoInsert: Boolean = true) {
        stopSession()
        autoInsertOnDone = autoInsert
        val bt = adapter
        when {
            bt == null -> { _state.value = State.Failed("此设备不支持蓝牙"); return }
            !bt.isEnabled -> { _state.value = State.Failed("请先打开手机蓝牙"); return }
        }
        sessionActive = true
        stableWeightKg = null
        lastComposition = null
        impedanceWaitJob?.cancel()
        impedanceWaitJob = null
        log("── 会话开始 ──")
        startScan()
        scanTimeoutJob = appScope.launch {
            repeat(SCAN_TIMEOUT_SECONDS) {
                delay(1000)
                val s = _state.value
                if (s is State.Scanning) _state.value = s.copy(secondsLeft = s.secondsLeft - 1)
            }
            if (_state.value is State.Scanning || _state.value is State.Connecting) {
                fail("60 秒内未找到秤，请确认秤已装电池并踩一下唤醒")
            }
        }
    }

    /** 结束会话并释放蓝牙资源；幂等 */
    fun stopSession() {
        sessionActive = false
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        runCatching { gatt?.close() }
        gatt = null
        if (_state.value !is State.Done && _state.value !is State.Failed) {
            _state.value = State.Idle
        }
    }

    // ---------------- 扫描 ----------------

    private fun startScan() {
        _state.value = State.Scanning(secondsLeft = SCAN_TIMEOUT_SECONDS)
        val filters = listOf(
            ScanFilter.Builder().setDeviceName(DEVICE_NAME).build(),
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            adapter?.bluetoothLeScanner?.startScan(filters, settings, scanCallback)
        } catch (e: Exception) {
            fail("扫描启动失败：${e.message ?: "未知错误"}")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (!sessionActive) return
            if (_state.value !is State.Scanning) return
            log("广播 ${result.device.address} rssi=${result.rssi}")
            connect(result)
        }

        override fun onScanFailed(errorCode: Int) {
            fail("扫描失败（错误码 $errorCode）")
        }
    }

    // ---------------- 连接与订阅 ----------------

    @SuppressLint("MissingPermission")
    private fun connect(result: ScanResult) {
        _state.value = State.Connecting
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        val device = result.device
        // 缺 BLUETOOTH_CONNECT 运行时权限时 connectGatt 抛 SecurityException，兜底成可读错误而不是崩溃
        gatt = runCatching {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }.getOrElse {
            fail("缺少蓝牙连接权限，请在系统设置中授权「附近的设备」")
            null
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    log("GATT 已连接，发现服务…")
                    g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    log("GATT 断开 status=$status")
                    if (sessionActive && _state.value !is State.Done) {
                        fail("与秤的连接断开")
                    }
                    runCatching { g.close() }
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                fail("服务发现失败（status=$status）")
                return
            }
            val service = g.getService(serviceUuid)
            if (service == null) {
                val found = g.services.joinToString { it.uuid.toString().take(8) }
                fail("秤上没有 FFB0 服务（实际：$found）")
                return
            }
            val notifyChar = service.getCharacteristic(notifyCharUuid)
            if (notifyChar == null) {
                fail("秤上没有 FFB2 通知特征")
                return
            }
            g.setCharacteristicNotification(notifyChar, true)
            val ccc = notifyChar.getDescriptor(cccDescriptorUuid)
            if (ccc != null) {
                ccc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                if (!g.writeDescriptor(ccc)) fail("订阅通知失败")
            } else {
                // 个别固件无 CCCD，直接视作可收通知
                onNotifyEnabled(g)
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (descriptor.characteristic.uuid == notifyCharUuid) {
                if (status == BluetoothGatt.GATT_SUCCESS) onNotifyEnabled(g)
                else fail("订阅通知失败（status=$status）")
            }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            log("写 ${characteristic.uuid.toString().take(8)} status=$status")
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handleFrame(value)
        }
    }

    /**
     * 通知就绪：下发 AC 27 用户档案（秤收到档案才做 BIA 测阻抗），写特征用 no-response
     * （本机实测 write-default 被拒 status=3，同家族厨房秤项目亦用 no-response）。
     */
    private fun onNotifyEnabled(g: android.bluetooth.BluetoothGatt) {
        val name = g.device.name ?: DEVICE_NAME
        _state.value = State.Ready(deviceName = name)
        log("通知已开启，下发用户档案")
        val writeChar = g.getService(serviceUuid)?.getCharacteristic(writeCharUuid)
        if (writeChar == null) {
            log("无 FFB1 写特征，跳过档案下发（仅记录体重）")
            return
        }
        appScope.launch {
            val refWeight = runCatching { recordDao.getLastData()?.weight }.getOrNull() ?: 70.0
            val profile = IcomonFrameParser.buildAc27ProfileCommand(
                sexMale = LocalStorageData.gender.value != "FEMALE",
                age = LocalStorageData.age.value,
                heightCm = LocalStorageData.height.value.toInt(),
                refWeightKg = refWeight,
            )
            log("写档案 ${hex(profile)}")
            writeChar.value = profile
            writeChar.writeType = android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            runCatching { g.writeCharacteristic(writeChar) }
                .onFailure { log("档案写入异常：${it.message}") }
        }
    }

    // ---------------- 数据解析与入库 ----------------

    /** 稳定帧先到、阻抗结果帧晚约 3 秒：暂存稳定值，等阻抗或超时再入库 */
    private var stableWeightKg: Double? = null
    private var impedanceWaitJob: Job? = null

    private fun handleFrame(payload: ByteArray) {
        log("通知 ${hex(payload)}")
        val m = IcomonFrameParser.parse(payload)
        if (m == null) {
            log("↑ 未能识别")
            return
        }
        when {
            m.weightKg > 0 && !m.isFinal ->
                _state.value = State.Measuring(weightKg = round01(m.weightKg))

            m.isResultFrame -> {
                impedanceWaitJob?.cancel()
                val weight = m.weightKg.takeIf { it > 0 } ?: stableWeightKg
                if (weight != null) {
                    finalize(weight, m.impedanceOhm)
                } else {
                    log("结果帧缺体重且无稳定值，忽略")
                }
            }

            m.weightKg > 0 && m.isFinal -> {
                stableWeightKg = m.weightKg
                _state.value = State.Stabilized(weightKg = round01(m.weightKg))
                impedanceWaitJob?.cancel()
                impedanceWaitJob = appScope.launch {
                    delay(IMPEDANCE_WAIT_MS)
                    log("等阻抗超时，仅按体重入库")
                    finalize(m.weightKg, null)
                }
            }

            else -> log("成分包：肌肉=${m.muscleRatio} 骨骼=${m.boneKg} 水分=${m.waterRatio}")
        }
    }

    private fun finalize(weightKg: Double, impedanceOhm: Double?) {
        if (!sessionActive) return
        sessionActive = false
        impedanceWaitJob?.cancel()
        val rounded = round01(weightKg)
        appScope.launch {
            // 阻抗 + 身体档案 → 全套身体成分；档案不全或阻抗缺测时仅存阻抗/不存成分
            val composition = impedanceOhm?.let { z ->
                BodyFatCalculator.calculate(
                    sexMale = LocalStorageData.gender.value != "FEMALE",
                    age = LocalStorageData.age.value,
                    heightCm = LocalStorageData.height.value.toInt(),
                    weightKg = rounded,
                    impedanceOhm = z,
                )?.copy(impedance = z.toInt())
                    ?: BodyComposition(impedance = z.toInt())
            }
            lastComposition = composition

            if (autoInsertOnDone) {
                // 防重复：秤可能重发稳定帧，或上一次会话刚记过同值——2 分钟内同重量直接跳过
                val last = recordDao.getLastData()
                val duplicate = last != null &&
                    kotlin.math.abs(last.weight - rounded) < 0.05 &&
                    System.currentTimeMillis() - last.timestamp < DEDUP_WINDOW_MS
                if (!duplicate) {
                    recordDao.insert(
                        Record(
                            weight = rounded,
                            log = "",
                            timestamp = System.currentTimeMillis(),
                            bodyComposition = composition?.let { BodyCompositionJson.encode(it) } ?: "",
                        )
                    )
                } else {
                    log("与最近记录重复（${last?.weight}kg），跳过入库")
                }
            } else {
                log("读秤完成，等待用户在弹窗中保存")
            }
            _state.value = State.Done(
                weightKg = rounded,
                fatRatio = composition?.fatRatio?.takeIf { it > 0 },
                impedanceOhm = impedanceOhm,
            )
            if (autoInsertOnDone) onRecordInserted?.invoke(rounded)
        }
    }

    private fun fail(message: String) {
        if (!sessionActive) return
        sessionActive = false
        scanTimeoutJob?.cancel()
        _state.value = State.Failed(message)
    }

    private fun log(line: String) {
        // Log.d 在 MIUI 上默认被吞，用 info 级保证真机抓包可见
        android.util.Log.i("ScaleBle", line)
        _rawLog.value = (_rawLog.value + line).takeLast(MAX_LOG_LINES)
    }

    private fun round01(v: Double) = kotlin.math.round(v * 10) / 10

    private fun hex(p: ByteArray) = p.joinToString(" ") { "%02X".format(it) }

    companion object {
        const val DEVICE_NAME = "icomon"
        private const val SCAN_TIMEOUT_SECONDS = 60
        private const val MAX_LOG_LINES = 200

        /** 防重复入库窗口：同一重量 2 分钟内只记一次 */
        private const val DEDUP_WINDOW_MS = 2 * 60 * 1000L

        /** 稳定体重帧后等待阻抗结果帧的窗口；超时仅按体重入库 */
        private const val IMPEDANCE_WAIT_MS = 5_000L
    }
}
