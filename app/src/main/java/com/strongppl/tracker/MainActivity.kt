package com.strongppl.tracker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.strongppl.tracker.data.*
import com.strongppl.tracker.domain.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = WorkoutDatabase.get(this)
        val store = SettingsStore(this)
        setContent {
            val vm: TrackerViewModel = viewModel(factory = TrackerViewModel.Factory(db.workoutDao(), store))
            val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { vm.exportTo(it, contentResolver) } }
            val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.importFrom(it, contentResolver) } }
            TrackerApp(vm, onExport = { export.launch("strong-ppl-backup-${LocalDate.now()}.json") }, onImport = { import.launch(arrayOf("application/json", "text/*")) })
        }
    }
}

class TrackerViewModel(private val dao: WorkoutDao, private val store: SettingsStore) : ViewModel() {
    private val today = LocalDate.now()
    private val _anchor = MutableStateFlow(WorkoutPlan.currentWeekMonday(today))
    val anchor = _anchor.asStateFlow()
    val allSets = dao.observeAllSets().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val sessions = dao.observeSessions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _selectedDate = MutableStateFlow(today)
    val selectedDate = _selectedDate.asStateFlow()
    init { viewModelScope.launch { _anchor.value = store.getOrCreateAnchor(today) } }
    fun selectDate(d: LocalDate) { _selectedDate.value = d }
    fun saveSet(date: LocalDate, exercise: ExercisePlan, setNo: Int, reps: String, weight: String, rir: String, band: String) {
        viewModelScope.launch {
            val r = reps.toIntOrNull()?.takeIf { it in 0..500 }
            val w = weight.toDoubleOrNull()?.takeIf { it in 0.0..1000.0 }
            val effort = rir.toIntOrNull()?.takeIf { it in 0..10 }
            if (reps.isBlank() && weight.isBlank() && rir.isBlank() && band.isBlank()) dao.deleteSet(date.toString(), exercise.id, setNo)
            else dao.saveSet(SetLog(date.toString(), exercise.id, exercise.name, setNo, r, w, effort, band.ifBlank { null }))
            if (dao.session(date.toString()) == null) dao.saveSession(WorkoutSession(date.toString(), WorkoutPlan.forDate(date).title))
        }
    }
    fun complete(date: LocalDate) { viewModelScope.launch { dao.saveSession(WorkoutSession(date.toString(), WorkoutPlan.forDate(date).title, "COMPLETED", completedAt = System.currentTimeMillis())) } }
    suspend fun previous(exerciseId: String, date: LocalDate) = dao.previousSets(exerciseId, date.toString())
    fun exportTo(uri: Uri, resolver: android.content.ContentResolver) { viewModelScope.launch {
        val root = JSONObject().put("schemaVersion", 1).put("app", "Strong PPL").put("exportedAt", System.currentTimeMillis())
        val sets = JSONArray(); dao.allSets().forEach { sets.put(JSONObject().put("date",it.date).put("exerciseId",it.exerciseId).put("exerciseName",it.exerciseName).put("setNumber",it.setNumber).put("reps",it.reps ?: JSONObject.NULL).put("weightKg",it.weightKg ?: JSONObject.NULL).put("rir",it.rir ?: JSONObject.NULL).put("band",it.band ?: JSONObject.NULL).put("notes",it.notes ?: JSONObject.NULL).put("updatedAt",it.updatedAt)) }
        val sessions = JSONArray(); dao.allSessions().forEach { sessions.put(JSONObject().put("date",it.date).put("title",it.title).put("status",it.status).put("startedAt",it.startedAt).put("completedAt",it.completedAt ?: JSONObject.NULL)) }
        root.put("sets",sets).put("sessions",sessions)
        resolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(root.toString(2)) }
    } }
    fun importFrom(uri: Uri, resolver: android.content.ContentResolver) { viewModelScope.launch {
        runCatching {
            val raw = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("Cannot read backup")
            val root = JSONObject(raw); require(root.getInt("schemaVersion") == 1) { "Unsupported backup version" }
            val sets = mutableListOf<SetLog>(); val a = root.getJSONArray("sets")
            require(a.length() <= 10000) { "Backup contains too many set records" }
            for (i in 0 until a.length()) {
                val o=a.getJSONObject(i); val d=o.getString("date"); java.time.LocalDate.parse(d)
                val item=SetLog(d,o.getString("exerciseId"),o.getString("exerciseName"),o.getInt("setNumber"),o.optIntNullable("reps"),o.optDoubleNullable("weightKg"),o.optIntNullable("rir"),o.optString("band").takeIf { it.isNotBlank() },o.optString("notes").takeIf { it.isNotBlank() },o.optLong("updatedAt"))
                require(item.setNumber in 1..10 && (item.reps == null || item.reps in 0..500) && (item.weightKg == null || item.weightKg in 0.0..1000.0) && (item.rir == null || item.rir in 0..10)) { "Invalid set record in backup" }
                sets += item
            }
            val sessions = mutableListOf<WorkoutSession>(); val b=root.getJSONArray("sessions")
            require(b.length() <= 1000) { "Backup contains too many sessions" }
            for(i in 0 until b.length()){val o=b.getJSONObject(i); val d=o.getString("date"); java.time.LocalDate.parse(d); sessions += WorkoutSession(d,o.getString("title"),o.getString("status"),o.optLong("startedAt"),o.optLongNullable("completedAt"))}
            require(sets.distinctBy { Triple(it.date,it.exerciseId,it.setNumber) }.size == sets.size) { "Duplicate set records in backup" }
            require(sessions.distinctBy { it.date }.size == sessions.size) { "Duplicate workout dates in backup" }
            dao.replaceAll(sets,sessions)
        }
    } }
    class Factory(private val dao: WorkoutDao, private val store: SettingsStore): ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>): T = TrackerViewModel(dao,store) as T
    }
}
private fun JSONObject.optIntNullable(k:String)=if(isNull(k)||!has(k)) null else optInt(k)
private fun JSONObject.optDoubleNullable(k:String)=if(isNull(k)||!has(k)) null else optDouble(k)
private fun JSONObject.optLongNullable(k:String)=if(isNull(k)||!has(k)) null else optLong(k)

private val Navy = Color(0xFF142B49)
private val Canvas = Color(0xFF0B1220)
private val Card = Color(0xFF17243A)
private val Muted = Color(0xFF9AAAC0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun TrackerApp(vm: TrackerViewModel, onExport:()->Unit, onImport:()->Unit) {
    var tab by remember { mutableStateOf("Today") }
    val date by vm.selectedDate.collectAsState(); val logs by vm.allSets.collectAsState(); val sessions by vm.sessions.collectAsState()
    val anchor by vm.anchor.collectAsState(); val day = WorkoutPlan.forDate(date); val week = WorkoutPlan.programmeWeek(date,anchor)
    MaterialTheme(colorScheme = darkColorScheme(primary=Color(0xFF8DB8FF), secondary=Color(0xFF75D6B1), background=Canvas, surface=Card)) {
        Scaffold(containerColor=Canvas, topBar={ TopAppBar(title={ Column { Text("STRONG PPL",fontWeight=FontWeight.Black); Text("12-week training log",style=MaterialTheme.typography.labelSmall,color=Muted) } }, colors=TopAppBarDefaults.topAppBarColors(containerColor=Canvas, titleContentColor=Color.White)) },
            bottomBar={ NavigationBar(containerColor=Card) { listOf("Today","Plan","History","Insights","Backup").forEach { item -> NavigationBarItem(selected=tab==item,onClick={tab=item},icon={ Icon(when(item){"Today"->Icons.Default.FitnessCenter;"Plan"->Icons.Default.CalendarMonth;"History"->Icons.Default.History;"Insights"->Icons.Default.ShowChart;else->Icons.Default.Backup},contentDescription=item) },label={Text(item)}) } } }) { padding ->
            when(tab) {
                "Today" -> TodayScreen(Modifier.padding(padding),date,week,day,logs,sessions,vm)
                "Plan" -> PlanScreen(Modifier.padding(padding),date,week,onDate={vm.selectDate(it)})
                "History" -> HistoryScreen(Modifier.padding(padding),sessions,logs)
                "Insights" -> InsightsScreen(Modifier.padding(padding),logs,sessions)
                else -> BackupScreen(Modifier.padding(padding),onExport,onImport)
            }
        }
    }
}

@Composable private fun TodayScreen(mod:Modifier,date:LocalDate,week:Int,day:DayPlan,logs:List<SetLog>,sessions:List<WorkoutSession>,vm:TrackerViewModel) {
    val dateFmt=DateTimeFormatter.ofPattern("EEEE, d MMMM")
    LazyColumn(modifier=mod.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) { Column { Text(date.format(dateFmt),color=Muted); Text("Week $week of 12",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold,color=Color.White) }; AssistChip(onClick={vm.selectDate(LocalDate.now())},label={Text("Today")},leadingIcon={Icon(Icons.Default.Today,null)}) } }
        item { CardBox { Column(verticalArrangement=Arrangement.spacedBy(6.dp)) { Text(if(day.isRest) "RECOVERY DAY" else "TODAY'S WORKOUT",color=Muted,style=MaterialTheme.typography.labelMedium); Text(day.title,style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold); Text(day.focus,color=Muted); Text(if(day.isRest) "Rest, walk gently and recover. Your training schedule never shifts." else "${day.exercises.size} exercises · about 45 minutes",color=Color(0xFF8DB8FF)) } } }
        if(day.isRest) item { CardBox { Text("Sunday is always rest. Monday starts the next training week.",color=Color.White) } }
        items(day.exercises,key={it.id}) { ex ->
            val exerciseLogs=logs.filter { it.date==date.toString() && it.exerciseId==ex.id }.associateBy { it.setNumber }
            var previous by remember(ex.id,date) { mutableStateOf<List<SetLog>>(emptyList()) }
            LaunchedEffect(ex.id,date) { previous=vm.previous(ex.id,date) }
            ExerciseCard(ex,exerciseLogs,previous) { n,r,w,rir,band -> vm.saveSet(date,ex,n,r,w,rir,band) }
        }
        if(!day.isRest) item { Button(onClick={vm.complete(date)},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)) { Icon(Icons.Default.CheckCircle,null); Spacer(Modifier.width(8.dp)); Text(if(sessions.any{it.date==date.toString()&&it.status=="COMPLETED"}) "Workout completed ✓" else "Finish workout") } }
        item { Text("Calendar is fixed by weekday. Missing a session never moves another workout.",color=Muted,style=MaterialTheme.typography.bodySmall) }
    }
}

@Composable private fun ExerciseCard(ex:ExercisePlan,logs:Map<Int,SetLog>,previous:List<SetLog>,onSave:(Int,String,String,String,String)->Unit) {
    CardBox {
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(ex.name,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,color=Color.White)
            Row(verticalAlignment=Alignment.CenterVertically) { Text("${ex.sets} × ${ex.minReps}–${ex.maxReps} reps",color=Color(0xFF8DB8FF),fontWeight=FontWeight.Bold); Spacer(Modifier.weight(1f)); Text("TARGET",color=Muted,style=MaterialTheme.typography.labelSmall) }
            if(ex.note.isNotBlank()) Text(ex.note,color=Color(0xFFFFD28A),style=MaterialTheme.typography.bodySmall)
            Text("SET     PREVIOUS       KG       REPS       RIR",color=Muted,style=MaterialTheme.typography.labelSmall)
            repeat(ex.sets) { idx ->
                val n=idx+1; val log=logs[n]; val prev=previous.firstOrNull{it.setNumber==n}
                var kg by remember(ex.id,date,n) { mutableStateOf(log?.weightKg?.toString().orEmpty()) }
                var reps by remember(ex.id,date,n) { mutableStateOf(log?.reps?.toString().orEmpty()) }
                var rir by remember(ex.id,date,n) { mutableStateOf(log?.rir?.toString().orEmpty()) }
                var band by remember(ex.id,date,n) { mutableStateOf(log?.band.orEmpty()) }
                var dirty by remember(ex.id,date,n) { mutableStateOf(false) }
                LaunchedEffect(log?.updatedAt) { if (!dirty && log != null) { kg=log?.weightKg?.toString().orEmpty(); reps=log?.reps?.toString().orEmpty(); rir=log?.rir?.toString().orEmpty(); band=log?.band.orEmpty() } }
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text("$n",modifier=Modifier.width(22.dp),color=Color.White,fontWeight=FontWeight.Bold)
                    Text(prev?.let { "${it.weightKg?.let { w->"${w}kg" } ?: "—"} × ${it.reps ?: "—"}" } ?: "—",modifier=Modifier.width(68.dp),color=Muted,style=MaterialTheme.typography.labelSmall)
                    OutlinedField(kg,{kg=it;dirty=true;onSave(n,reps,kg,rir,band)},"kg",Modifier.weight(1f),true)
                    OutlinedField(reps,{reps=it;dirty=true;onSave(n,reps,kg,rir,band)},"reps",Modifier.weight(1f),false)
                    OutlinedField(rir,{rir=it;dirty=true;onSave(n,reps,kg,rir,band)},"RIR",Modifier.width(54.dp),false)
                }
                OutlinedTextField(value=band,onValueChange={band=it;dirty=true;onSave(n,reps,kg,rir,band)},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("Band / setup (optional)")},textStyle=MaterialTheme.typography.bodySmall)
            }
            if(logs.isNotEmpty()) Text("Saved automatically",color=Color(0xFF75D6B1),style=MaterialTheme.typography.labelSmall)
        }
    }
}
@Composable private fun OutlinedField(value:String,onChange:(String)->Unit,label:String,modifier:Modifier,decimal:Boolean) {
    OutlinedTextField(value=value,onValueChange={s-> if(s.length<=7 && (s.isEmpty() || if(decimal) s.matches(Regex("\\d{0,4}(\\.\\d{0,2})?")) else s.all(Char::isDigit))) onChange(s)},modifier=modifier, singleLine=true,label={Text(label,style=MaterialTheme.typography.labelSmall)},keyboardOptions=KeyboardOptions(keyboardType=if(decimal) KeyboardType.Decimal else KeyboardType.Number),textStyle=MaterialTheme.typography.bodyMedium)
}
@Composable private fun PlanScreen(mod:Modifier,date:LocalDate,week:Int,onDate:(LocalDate)->Unit) {
    LazyColumn(modifier=mod.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item { Text("12-week programme",style=MaterialTheme.typography.headlineMedium,color=Color.White,fontWeight=FontWeight.Bold); Text("Week $week · fixed Monday start",color=Muted) }
        items(WorkoutPlan.days) { d -> CardBox { Column { Text(d.day.name.lowercase().replaceFirstChar{it.uppercase()},color=Muted); Text(d.title,style=MaterialTheme.typography.titleLarge,color=Color.White,fontWeight=FontWeight.Bold); Text(d.focus,color=Muted); if(d.exercises.isNotEmpty()) Text(d.exercises.joinToString(" · "){it.name},color=Color.White,style=MaterialTheme.typography.bodySmall) else Text("Rest and recovery",color=Color(0xFF75D6B1)); TextButton(onClick={onDate(date.with(java.time.temporal.TemporalAdjusters.previousOrSame(d.day)))}) { Text("View this weekday") } } } }
    }
}
@Composable private fun HistoryScreen(mod:Modifier,sessions:List<WorkoutSession>,logs:List<SetLog>) {
    LazyColumn(modifier=mod.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item { Text("Workout history",style=MaterialTheme.typography.headlineMedium,color=Color.White,fontWeight=FontWeight.Bold) }
        if(sessions.isEmpty()) item { CardBox { Text("Your completed and in-progress workouts will appear here.",color=Muted) } }
        items(sessions.sortedByDescending{it.date}) { s -> CardBox { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Column { Text(s.title,color=Color.White,fontWeight=FontWeight.Bold); Text(s.date,color=Muted) }; AssistChip(onClick={},label={Text(s.status.replace('_',' '))}) }; Text("${logs.count{it.date==s.date}} set entries",color=Muted) } }
    }
}
@Composable private fun InsightsScreen(mod:Modifier,logs:List<SetLog>,sessions:List<WorkoutSession>) {
    val total=logs.count{it.reps!=null}; val completed=sessions.count{it.status=="COMPLETED"}; val volume=logs.sumOf{(it.reps?:0)*(it.weightKg?:0.0)}
    LazyColumn(modifier=mod.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Text("Your progress",style=MaterialTheme.typography.headlineMedium,color=Color.White,fontWeight=FontWeight.Bold) }
        item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Metric("Completed",completed.toString(),Modifier.weight(1f)); Metric("Logged sets",total.toString(),Modifier.weight(1f)) } }
        item { CardBox { Text("Recorded load-volume",color=Muted); Text("${"%.1f".format(volume)} kg-reps",style=MaterialTheme.typography.headlineMedium,color=Color.White,fontWeight=FontWeight.Bold); Text("Calculated from sets with both reps and kg entered. Band-only sets are not assigned invented loads.",color=Muted,style=MaterialTheme.typography.bodySmall) } }
        item { CardBox { Text("Progression rule",color=Color.White,fontWeight=FontWeight.Bold); Text("Consider increasing resistance only when every prescribed set reaches the top of its rep range with good control and around 2–3 reps in reserve. Suggestions will never change your plan automatically.",color=Muted) } }
    }
}
@Composable private fun BackupScreen(mod:Modifier,onExport:()->Unit,onImport:()->Unit) {
    Column(mod.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Backup & restore",style=MaterialTheme.typography.headlineMedium,color=Color.White,fontWeight=FontWeight.Bold)
        CardBox { Text("Your workout records are stored on this phone. Export a backup regularly, especially before changing or resetting devices.",color=Muted) }
        Button(onClick=onExport,modifier=Modifier.fillMaxWidth()) { Icon(Icons.Default.FileDownload,null); Spacer(Modifier.width(8.dp)); Text("Export backup (.json)") }
        OutlinedButton(onClick=onImport,modifier=Modifier.fillMaxWidth()) { Icon(Icons.Default.FileUpload,null); Spacer(Modifier.width(8.dp)); Text("Restore from backup") }
        Text("Restore replaces the current local workout history. Export a backup first if you want to keep it.",color=Color(0xFFFFD28A),style=MaterialTheme.typography.bodySmall)
    }
}
@Composable private fun CardBox(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) { Surface(color=Card,shape=RoundedCornerShape(18.dp),modifier=modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),content=content) } }
@Composable private fun Metric(label:String,value:String,modifier:Modifier) { CardBox(modifier) { Text(label,color=Muted); Text(value,style=MaterialTheme.typography.headlineLarge,color=Color.White,fontWeight=FontWeight.Bold) } }
