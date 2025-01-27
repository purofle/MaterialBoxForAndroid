package io.nekohasekai.sagernet.ui

import android.Manifest.permission.POST_NOTIFICATIONS
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.RemoteException
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.aidl.ISagerNetService
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.SagerDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.matsuri.nb4a.utils.Util
import moe.matsuri.nb4a.utils.toBytesString
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSettings


class NewUIActivity : ComponentActivity(), SagerConnection.Callback {

    private val connection =
        SagerConnection(SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_FOREGROUND, true)

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= 33) {
            val checkPermission =
                ContextCompat.checkSelfPermission(this@NewUIActivity, POST_NOTIFICATIONS)
            if (checkPermission != PackageManager.PERMISSION_GRANTED) {
                //动态申请
                ActivityCompat.requestPermissions(
                    this@NewUIActivity, arrayOf(POST_NOTIFICATIONS), 0
                )
            }
        }

        connection.connect(this, this)

        setContent {

            val groupList = remember { mutableStateListOf<ProxyGroup>() }
            val configurationList = remember { mutableStateListOf<ProxyEntity>() }
            var selectedGroup by remember { mutableLongStateOf(0L) }

            val navController = rememberNavController()
            val scrollBehavior = BottomAppBarDefaults.exitAlwaysScrollBehavior()

            val snackbarHostState = remember { SnackbarHostState() }

            val scope = rememberCoroutineScope()

            val connect = rememberLauncherForActivityResult(VpnRequestActivity.StartService()) {
                if (it) scope.launch {
                    snackbarHostState.showSnackbar(getString(R.string.vpn_permission_denied))
                }
            }

            LaunchedEffect(Unit) {
                var newGroupList = ArrayList(SagerDatabase.groupDao.allGroups())
                Log.d(TAG, "onCreate: $newGroupList")
                if (newGroupList.isEmpty()) {
                    // for first launch
                    SagerDatabase.groupDao.createGroup(ProxyGroup(ungrouped = true))
                    newGroupList = ArrayList(SagerDatabase.groupDao.allGroups())
                }

                groupList.addAll(newGroupList)

                selectedGroup = DataStore.currentGroupId().takeIf { it > 0L }
                    ?: newGroupList.first().id.also { DataStore.selectedGroup = it }
                Log.d(TAG, "onCreate: $groupList")
            }

            LaunchedEffect(selectedGroup) {
                configurationList.clear()
                configurationList.addAll(SagerDatabase.proxyDao.getByGroup(selectedGroup))
            }

            val isDarkTheme = isSystemInDarkTheme()
            val supportsDynamicColor = Build.VERSION.SDK_INT == Build.VERSION_CODES.S
            val colorScheme = when {
                supportsDynamicColor && isDarkTheme -> {
                    dynamicDarkColorScheme(LocalContext.current)
                }

                supportsDynamicColor && !isDarkTheme -> {
                    dynamicLightColorScheme(LocalContext.current)
                }

                isDarkTheme -> darkColorScheme()
                else -> lightColorScheme()
            }

            MaterialTheme(
                colorScheme = colorScheme
            ) {
                val isCollapsed = scrollBehavior.state.collapsedFraction == 1.0f
                val listState = rememberLazyListState()
                val bottomScrollDispatcher = NestedScrollDispatcher()

                Scaffold(
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    modifier = Modifier.nestedScroll(
                        scrollBehavior.nestedScrollConnection,
                        bottomScrollDispatcher
                    ),
                    snackbarHost = {
                        SnackbarHost(hostState = snackbarHostState)
                    },
                    topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
                    bottomBar = {
                        AnimatedVisibility(!listState.canScrollBackward || !isCollapsed) {
                            BottomAppBar(
                                scrollBehavior = scrollBehavior,
                            ) {
                                NavigationBarItem(
                                    icon = {
                                        Icon(
                                            painterResource(R.drawable.ic_action_description),
                                            contentDescription = stringResource(R.string.menu_configuration)
                                        )
                                    },
                                    label = { Text(stringResource(R.string.menu_configuration)) },
                                    selected = true,
                                    onClick = { }
                                )
                                NavigationBarItem(
                                    icon = {
                                        Icon(
                                            painterResource(R.drawable.ic_maps_directions),
                                            contentDescription = stringResource(R.string.menu_route)
                                        )
                                    },
                                    label = { Text(stringResource(R.string.menu_route)) },
                                    selected = false,
                                    onClick = { }
                                )
                                NavigationBarItem(
                                    icon = {
                                        Icon(
                                            painterResource(R.drawable.ic_action_settings),
                                            contentDescription = stringResource(R.string.settings)
                                        )
                                    },
                                    label = { Text(stringResource(R.string.settings)) },
                                    selected = false,
                                    onClick = { }
                                )
                            }
                        }
                    }
                ) { pd ->
                    Column(
                        modifier = Modifier.padding(pd)
                    ) {
                        GroupSwitcher(
                            listState = listState,
                            groupList = groupList,
                            selectedGroup = selectedGroup,
                            onClick = {
                                scope.launch {
                                    listState.animateScrollToItem(0)
                                    bottomScrollDispatcher.dispatchPostScroll(
                                        consumed = Offset(
                                            0f,
                                            -scrollBehavior.state.heightOffsetLimit
                                        ),
                                        available = Offset.Zero,
                                        NestedScrollSource.SideEffect
                                    )
                                }
                            }
                        )

                        var selectedProxy by remember { mutableLongStateOf(-1) }

                        LazyColumnScrollbar(
                            state = listState,
                            settings = ScrollbarSettings.Default
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.nestedScroll(
                                    scrollBehavior.nestedScrollConnection,
                                    bottomScrollDispatcher
                                )
                            ) {
                                items(configurationList, key = {
                                    it.id
                                }) { item ->

                                    ConfigurationCard(
                                        proxyEntity = item,
                                        selected = selectedProxy == item.id

                                    ) {
                                        scope.launch {
                                            launchConfiguration(connect) {
                                                selectedProxy = item.id
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onServiceConnected(service: ISagerNetService) {
        DataStore.serviceState = try {
            BaseService.State.entries[service.state]
        } catch (_: RemoteException) {
            BaseService.State.Idle
        }
    }

    override fun stateChanged(state: BaseService.State, profileName: String?, msg: String?) {
        DataStore.serviceState = state
    }

    @Composable
    fun ConfigurationCard(
        proxyEntity: ProxyEntity,
        selected: Boolean = true,
        onClick: () -> Unit
    ) {

        val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer

        val textColor = contentColorFor(surfaceContainer)
        val trafficUplink = stringResource(R.string.traffic_uplink)

        val textMeasurer = rememberTextMeasurer()

        val style = TextStyle(
            fontSize = 16.sp,
            color = textColor,
        )
        val textLayoutResult = remember(trafficUplink, style) {
            textMeasurer.measure(trafficUplink, style)
        }

        val barAnimationProcess = remember {
            Animatable(0f)
        }

        LaunchedEffect(selected) {
            if (selected) {
                barAnimationProcess.animateTo(
                    1f,
                    animationSpec = tween(600, easing = FastOutSlowInEasing)
                )
            }
        }

        fun Modifier.selectedModifier(selected: Boolean) = if (selected) {
            then(
                Modifier
                    .drawBehind {
                        drawRoundRect(
                            color = surfaceContainer,
                            topLeft = Offset(0f, 16.dp.toPx()) * barAnimationProcess.value,
                            size = size.copy(height = size.height - 16.dp.toPx()),
                            cornerRadius = CornerRadius(8.dp.toPx())
                        )

                        if (barAnimationProcess.value > 0.7f) {
                            drawText(
                                textMeasurer = textMeasurer,
                                text = trafficUplink,
                                style = style,
                                topLeft = Offset(
                                    x = 8.dp.toPx(),
                                    //                (竖排居中)
                                    //  绘制区域高度  - (   刘海高     / 2 +              文字高度        )
                                    y = size.height - (16.dp.toPx() / 2 + textLayoutResult.size.height),
                                )
                            )
                        }
                    }
                    .padding(0.dp, 0.dp, 0.dp, 32.dp))
        } else {
            this
        }

        Card(
            modifier = Modifier
                .padding(16.dp, 8.dp)
                .clickable { onClick() }
                .selectedModifier(selected)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        proxyEntity.displayName(),
                        fontWeight = FontWeight.Bold
                    )
                    Text(proxyEntity.displayType())
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        proxyEntity.displayAddress(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        color = if (isSystemInDarkTheme()) Color.LightGray else Color.Gray

                    )
                    Row {
                        Text("114514ms", modifier = Modifier.padding(start = 8.dp))
                        Icon(
                            painterResource(R.drawable.ic_image_edit),
                            contentDescription = stringResource(R.string.edit),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalSharedTransitionApi::class)
    @Composable
    fun GroupSwitcher(
        listState: LazyListState,
        groupList: List<ProxyGroup>,
        selectedGroup: Long,
        onClick: () -> Unit = {}
    ) {

        val group = groupList.firstOrNull { it.id == selectedGroup }
        val trafficText = group?.subscription?.let { subscription ->
            if (subscription.bytesUsed > 0L) {
                if (subscription.bytesRemaining > 0L) {
                    stringResource(
                        R.string.subscription_traffic,
                        subscription.bytesUsed,
                        subscription.bytesRemaining
                    )
                } else {
                    stringResource(R.string.subscription_used, subscription.bytesUsed)
                }
            } else {
                if (!subscription.subscriptionUserinfo.isNullOrBlank()) {
                    var text = ""

                    fun get(regex: String): String? {
                        return regex.toRegex().findAll(subscription.subscriptionUserinfo)
                            .mapNotNull {
                                if (it.groupValues.size > 1) it.groupValues[1] else null
                            }.firstOrNull()
                    }
                    runCatching {
                        var used: Long = 0
                        get("upload=([0-9]+)")?.apply {
                            used += toLong()
                        }
                        get("download=([0-9]+)")?.apply {
                            used += toLong()
                        }
                        val total = get("total=([0-9]+)")?.toLong() ?: 0
                        if (used > 0 || total > 0) {
                            text += stringResource(
                                R.string.subscription_traffic,
                                used.toBytesString(),
                                (total - used).toBytesString()
                            )
                        }
                        get("expire=([0-9]+)")?.apply {
                            text += "\n"
                            text += stringResource(
                                R.string.subscription_expire,
                                Util.timeStamp2Text(this.toLong() * 1000)
                            )
                        }
                    }

                    text
                } else {
                    ""
                }
            }
        }

        SharedTransitionLayout {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp, 8.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable { onClick() },
            ) {
                val isExpanded = listState.canScrollBackward
                val boundsTransform = { _: Rect, _: Rect -> tween<Rect>(550) }

                AnimatedContent(
                    targetState = isExpanded,
                    label = "topBar"
                ) { target ->
                    if (target) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    group?.name
                                        ?: stringResource(R.string.group_default),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.sharedElement(
                                        state = rememberSharedContentState("group_name"),
                                        animatedVisibilityScope = this@AnimatedContent,
                                        boundsTransform = boundsTransform,
                                    )
                                )
                                trafficText?.let {
                                    Text(
                                        it,
                                        modifier = Modifier.sharedElement(
                                            state = rememberSharedContentState("traffic"),
                                            animatedVisibilityScope = this@AnimatedContent,
                                            boundsTransform = boundsTransform,
                                        ),
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(
                                    group?.name
                                        ?: stringResource(R.string.group_default),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.sharedElement(
                                        state = rememberSharedContentState("group_name"),
                                        animatedVisibilityScope = this@AnimatedContent,
                                        boundsTransform = boundsTransform,
                                    )
                                )
                                trafficText?.let {
                                    Text(
                                        it,
                                        modifier = Modifier.sharedElement(
                                            state = rememberSharedContentState("traffic"),
                                            animatedVisibilityScope = this@AnimatedContent,
                                            boundsTransform = boundsTransform,
                                        ),
                                        fontSize = 14.sp
                                    )
                                }
                            }
                            val surfaceContainer =
                                MaterialTheme.colorScheme.surfaceContainer
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.edit),
                                modifier = Modifier
                                    .drawBehind {
                                        drawCircle(
                                            surfaceContainer,
                                            radius = 24.dp.toPx(),
                                        )
                                    }
                                    .padding(12.dp, 0.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    private suspend fun launchConfiguration(
        connect: ManagedActivityResultLauncher<Void?, Boolean>,
        callback: () -> Unit
    ) {
        if (DataStore.serviceState.started) {
            SagerNet.stopService()
            delay(500) // wait for service stop
        }

        connect.launch(null)
        callback()
    }

    companion object {
        private const val TAG = "NewUIActivity"
    }
}