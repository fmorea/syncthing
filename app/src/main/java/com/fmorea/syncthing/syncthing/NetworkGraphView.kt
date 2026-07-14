package com.fmorea.syncthing.syncthing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
    val to: String
)

@Composable
fun NetworkGraphView(
    viewModel: LinkThingViewModel,
    modifier: Modifier = Modifier
) {
    val meshTopology by viewModel.meshTopology.collectAsState()
    val friends by viewModel.friends.collectAsState()
    val localDevice by viewModel.localDevice.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val friendProfiles by viewModel.friendProfiles.collectAsState()

    val nodes = remember { mutableStateListOf<GraphNode>() }
    val edges = remember { mutableStateListOf<GraphEdge>() }
    
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurface)

    // Initialize/Update nodes and edges when data changes
    LaunchedEffect(meshTopology, friends, localDevice) {
        val currentIds = nodes.map { it.id }.toSet()
        val allDevices = (friends.map { it.deviceID } + (localDevice?.deviceID ?: "")).filter { it.isNotBlank() }.toSet()
        val meshDevices = meshTopology.keys + meshTopology.values
        val totalDevices = (allDevices + meshDevices).filter { it.isNotBlank() }.toSet()

        // Add new nodes
        totalDevices.forEach { id ->
            if (id !in currentIds) {
                val label = if (id == localDevice?.deviceID) userProfile.getDisplayName() 
                            else friendProfiles[id]?.getDisplayName() ?: id.take(6)
                nodes.add(GraphNode(id, label, Offset(300f + (Math.random() * 100).toFloat(), 300f + (Math.random() * 100).toFloat())))
            }
        }

        // Remove old nodes
        nodes.removeAll { it.id !in totalDevices }

        // Update edges
        edges.clear()
        meshTopology.forEach { (child, parent) ->
            if (child.isNotBlank() && parent.isNotBlank()) {
                edges.add(GraphEdge(parent, child))
            }
        }
    }

    var viewportSize by remember { mutableStateOf(Offset(1000f, 1000f)) }

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
    val edgeColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
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
                val start = nodes.find { it.id == edge.from }?.position
                val end = nodes.find { it.id == edge.to }?.position
                if (start != null && end != null) {
                    val angle = atan2(end.y - start.y, end.x - start.x)
                    
                    // Stop edge before node center to show arrow clearly
                    val arrowOffset = 25f 
                    val arrowEnd = Offset(
                        end.x - arrowOffset * cos(angle),
                        end.y - arrowOffset * sin(angle)
                    )

                    drawLine(
                        color = edgeColor,
                        start = start,
                        end = arrowEnd,
                        strokeWidth = 2f
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
                    
                    drawLine(color = edgeColor, start = arrowEnd, end = p1, strokeWidth = 2f)
                    drawLine(color = edgeColor, start = arrowEnd, end = p2, strokeWidth = 2f)
                }
            }

            // Draw Nodes
            nodes.forEach { node ->
                val color = if (node.id == localDevice?.deviceID) primaryColor else secondaryColor
                
                drawCircle(
                    color = color,
                    radius = 20f,
                    center = node.position
                )
                
                drawCircle(
                    color = color.copy(alpha = 0.3f),
                    radius = 25f,
                    center = node.position,
                    style = Stroke(width = 2f)
                )

                val textLayoutResult = textMeasurer.measure(node.label, labelStyle)
                drawText(
                    textLayoutResult,
                    topLeft = node.position + Offset(-textLayoutResult.size.width / 2f, 25f)
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
