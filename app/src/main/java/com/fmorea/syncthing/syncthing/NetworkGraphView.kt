package com.fmorea.syncthing.syncthing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fmorea.syncthing.service.Constants
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class GraphNode(
    val id: String,
    val label: String,
    var position: Offset,
    var velocity: Offset = Offset.Zero
)

data class GraphEdge(
    val from: String,
    val to: String,
    val label: String
)

@Composable
fun NetworkGraphView(
    viewModel: LinkThingViewModel,
    modifier: Modifier = Modifier,
    onNodeClick: (String) -> Unit = {}
) {
    val meshEdges by viewModel.meshEdges.collectAsState()
    val friends by viewModel.friends.collectAsState()
    val localDevice by viewModel.localDevice.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val friendProfiles by viewModel.friendProfiles.collectAsState()

    val nodes = remember { mutableStateListOf<GraphNode>() }
    val edges = remember { mutableStateListOf<GraphEdge>() }
    
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurface)

    var viewportSize by remember { mutableStateOf(Offset(800f, 800f)) }

    // Helper to get device by ID
    fun getDevice(id: String) = if (id == localDevice?.deviceID) localDevice else friends.find { it.deviceID == id }

    val localDeviceID = viewModel.getLocalDeviceId()

    // Initialize/Update nodes and edges when data changes
    LaunchedEffect(meshEdges, friends, localDevice) {
        val currentIds = nodes.map { it.id }.toSet()
        val allDevices = (friends.map { it.deviceID } + (localDevice?.deviceID ?: localDeviceID)).filter { it.isNotBlank() }.toSet()
        val meshDevices = meshEdges.flatMap { listOf(it.first, it.second) }.toSet()
        val totalDevices = (allDevices + meshDevices).filter { it.isNotBlank() }.toSet()

        // Add new nodes
        totalDevices.forEach { id ->
            if (id !in currentIds) {
                val label = if (id == localDevice?.deviceID || id == localDeviceID) userProfile.getDisplayName() 
                            else friendProfiles[id]?.getDisplayName() ?: id.take(6)
                nodes.add(GraphNode(id, label, Offset(viewportSize.x / 2f + (Math.random() * 200 - 100).toFloat(), viewportSize.y / 2f + (Math.random() * 200 - 100).toFloat())))
            }
        }

        // Remove old nodes
        nodes.removeAll { it.id !in totalDevices }

        // Update edges
        edges.clear()
        meshEdges.forEach { (parent, child, label) ->
            if (parent.isNotBlank() && child.isNotBlank()) {
                edges.add(GraphEdge(parent, child, label))
            }
        }
    }

    // Force refresh beacons on composition
    LaunchedEffect(Unit) {
        viewModel.refreshFriends()
    }

    // Physics Engine Simulation
    LaunchedEffect(viewportSize) {
        while (true) {
            val repulsionStrength = 3000f
            val springStrength = 0.04f
            val damping = 0.6f
            val idealDistance = 160f

            val center = Offset(viewportSize.x / 2f, viewportSize.y / 2f)

            for (i in nodes.indices) {
                val nodeA = nodes[i]
                var force = Offset.Zero

                // Repulsion from other nodes
                for (j in nodes.indices) {
                    if (i == j) continue
                    val nodeB = nodes[j]
                    val delta = nodeA.position - nodeB.position
                    val distance = delta.getDistance().coerceAtLeast(1f)
                    if (distance < 500f) {
                        force += delta / (distance * distance) * repulsionStrength
                    }
                }

                // Spring forces from edges
                edges.forEach { edge ->
                    if (edge.from == nodeA.id || edge.to == nodeA.id) {
                        val otherId = if (edge.from == nodeA.id) edge.to else edge.from
                        val nodeB = nodes.find { it.id == otherId }
                        if (nodeB != null) {
                            val delta = nodeB.position - nodeA.position
                            val distance = delta.getDistance().coerceAtLeast(1f)
                            val displacement = distance - idealDistance
                            force += delta / distance * displacement * springStrength
                        }
                    }
                }

                // Center gravity
                val centerDelta = center - nodeA.position
                force += centerDelta * 0.005f

                val newVelocity = (nodeA.velocity + force) * damping
                // Limit max velocity to prevent explosions
                val speed = newVelocity.getDistance()
                val cappedVelocity = if (speed > 10f) newVelocity / speed * 10f else newVelocity
                
                val newPosition = nodeA.position + cappedVelocity
                
                nodes[i] = nodeA.copy(position = newPosition, velocity = cappedVelocity)
            }
            delay(20)
        }
    }

    var draggedNode by remember { mutableStateOf<String?>(null) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val edgeColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val clickedNode = nodes.find { (it.position - offset).getDistance() < 50f }
                        clickedNode?.let { onNodeClick(it.id) }
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            draggedNode = nodes.find { (it.position - offset).getDistance() < 50f }?.id
                        },
                        onDrag = { _, dragAmount ->
                            draggedNode?.let { id ->
                                val index = nodes.indexOfFirst { it.id == id }
                                if (index != -1) {
                                    val node = nodes[index]
                                    nodes[index] = node.copy(position = node.position + dragAmount, velocity = Offset.Zero)
                                }
                            }
                        },
                        onDragEnd = { draggedNode = null }
                    )
                }
        ) {
            viewportSize = Offset(size.width, size.height)
            
            // Draw Edges with Directional Arrows
            edges.forEach { edge ->
                val startNode = nodes.find { it.id == edge.from }
                val endNode = nodes.find { it.id == edge.to }
                var start = startNode?.position
                var end = endNode?.position
                
                if (start != null && end != null) {
                    // Check if there is a reverse edge
                    val hasReverse = edges.any { it.from == edge.to && it.to == edge.from }
                    
                    val angle = atan2(end.y - start.y, end.x - start.x)
                    
                    // If bidirectional, offset the line slightly to the right of its direction
                    if (hasReverse) {
                        val offsetDistance = 6f
                        val offsetX = offsetDistance * cos(angle + Math.PI / 2).toFloat()
                        val offsetY = offsetDistance * sin(angle + Math.PI / 2).toFloat()
                        start = Offset(start.x + offsetX, start.y + offsetY)
                        end = Offset(end.x + offsetX, end.y + offsetY)
                    }

                    val isEndIntroducer = getDevice(edge.to)?.introducer == true
                    val endRadius = if (isEndIntroducer) 40f else 25f

                    // Stop edge before node center to show arrow clearly
                    val arrowOffset = endRadius + 5f 
                    val arrowEnd = Offset(
                        end.x - arrowOffset * cos(angle).toFloat(),
                        end.y - arrowOffset * sin(angle).toFloat()
                    )

                    drawLine(
                        color = edgeColor,
                        start = start,
                        end = arrowEnd,
                        strokeWidth = 2.5f
                    )
                    
                    // Draw arrow head
                    val arrowSize = 15f
                    val arrowAngle = Math.PI / 6
                    
                    val p1 = Offset(
                        arrowEnd.x - arrowSize * cos(angle - arrowAngle).toFloat(),
                        arrowEnd.y - arrowSize * sin(angle - arrowAngle).toFloat()
                    )
                    val p2 = Offset(
                        arrowEnd.x - arrowSize * cos(angle + arrowAngle).toFloat(),
                        arrowEnd.y - arrowSize * sin(angle + arrowAngle).toFloat()
                    )
                    
                    drawLine(color = edgeColor, start = arrowEnd, end = p1, strokeWidth = 2.5f)
                    drawLine(color = edgeColor, start = arrowEnd, end = p2, strokeWidth = 2.5f)
                }
            }

            // Draw Nodes
            nodes.forEach { node ->
                val device = getDevice(node.id)
                val isBootstrap = Constants.isBootstrapId(node.id)
                val isIntroducer = device?.introducer == true || isBootstrap
                val baseRadius = if (isBootstrap) 40f else if (isIntroducer) 30f else 20f
                val outerRadius = baseRadius + 5f

                val color = when {
                    node.id == localDevice?.deviceID -> primaryColor
                    isBootstrap -> Color(0xFF4CAF50) // Bootstrap is always prominent green
                    else -> secondaryColor
                }
                
                drawCircle(
                    color = color,
                    radius = baseRadius,
                    center = node.position
                )
                
                if (isBootstrap) {
                    drawCircle(
                        color = color,
                        radius = outerRadius + 5f,
                        center = node.position,
                        style = Stroke(width = 3f)
                    )
                }

                drawCircle(
                    color = color.copy(alpha = 0.3f),
                    radius = outerRadius,
                    center = node.position,
                    style = Stroke(width = 2f)
                )

                val displayLabel = if (isBootstrap) "BOOTSTRAPPER" else node.label
                val textLayoutResult = textMeasurer.measure(displayLabel, labelStyle)
                drawText(
                    textLayoutResult,
                    topLeft = node.position + Offset(-textLayoutResult.size.width / 2f, outerRadius + 5f)
                )
            }
        }
        
        Text(
            "Graph View (Beta)", 
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}
