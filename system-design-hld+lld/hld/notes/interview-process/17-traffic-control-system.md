# #17 traffic control system

Here’s a complete, time-boxed, 1-hour interview-ready answer for designing a Traffic Control System (smart city / intelligent traffic management). It follows your usual system design interview structure, including functional & non-functional requirements, APIs/data model, architecture, deep dive, and trade-offs.

***

## 0 – 5 min — Problem recap, scope & assumptions

<br>

Goal: Design a traffic control system to manage road intersections, monitor congestion, optimize traffic light signals, and support real-time alerts for emergency vehicles. System should improve traffic flow, reduce waiting times, and support both city-wide monitoring and localized optimization.

<br>

Scope for interview:

* Real-time traffic signal control for intersections.
* Traffic monitoring via sensors/cameras/IoT devices.
* Congestion prediction and adaptive signal timing.
* Emergency vehicle prioritization (green wave).
* Data collection for analytics & planning.

<br>

Assumptions:

* Target city: 1000 intersections.
* Each intersection has 4–6 traffic lights, 2–4 lanes per road.
* Vehicle detection via cameras, loop sensors, or radar.
* Latency requirement: signal updates within 1–2 sec of data capture for adaptive control.
* Peak load: morning/evening rush hours.

***

## 5 – 15 min — Functional & Non-Functional Requirements

<br>

#### Functional Requirements

<br>

Must

1. Traffic signal control: manage traffic lights for intersections.
2. Sensor data collection: ingest vehicle counts, speed, congestion, accidents.
3. Adaptive signal timing: dynamically adjust signal durations based on real-time traffic.
4. Emergency vehicle prioritization: detect emergency vehicles and grant green lights.
5. Traffic analytics: store historical traffic data for planning & optimization.
6. Alerts & notifications: send congestion or incident alerts to operators.
7. Manual override: operators can manually control signals if needed.

<br>

Should

* Support multi-modal traffic (cars, buses, bicycles, pedestrians).
* Integration with public transport schedules for signal prioritization.
* Predictive congestion modeling using ML.

<br>

Nice-to-have

* Dynamic rerouting suggestions to connected vehicles.
* Integration with autonomous vehicle systems.
* Multi-city traffic control coordination.

***

#### Non-Functional Requirements

* Availability: 99.9% uptime for signal control; failures should default to safe mode.
* Latency: adaptive updates within 1–2 sec; sensor ingestion near real-time.
* Scalability: support thousands of intersections, millions of vehicles/day.
* Durability: historical traffic data stored reliably.
* Fault tolerance: single intersection failure does not impact others.
* Consistency: traffic signal state consistency per intersection.
* Security: prevent unauthorized access to control system; secure IoT devices.
* Monitoring & observability: system health, sensor data quality, congestion metrics.

***

## 15 – 25 min — API / Data Model

<br>

#### Component APIs

* Traffic Sensor API

```
ingest_data(intersection_id, sensor_type, vehicle_count, avg_speed, timestamp)
```

* Traffic Signal Controller API

```
set_signal(intersection_id, direction, signal_state, duration)
get_signal_state(intersection_id) -> {direction: state, timestamp}
```

* Analytics / Dashboard API

```
get_congestion(intersection_id, time_range) -> congestion_level
get_historical_data(intersection_id, day_range) -> vehicle_count, avg_speed
```

* Emergency Vehicle API

```
notify_emergency_vehicle(location, eta) -> green_wave_signal_plan
```

#### Data Models

* Intersection

```
{
  "intersection_id": "string",
  "location": "lat/lng",
  "roads": ["road_id"],
  "traffic_lights": ["light_id"]
}
```

* Traffic Light

```
{
  "light_id": "string",
  "intersection_id": "string",
  "direction": "N/S/E/W",
  "state": "RED/YELLOW/GREEN",
  "last_updated": "timestamp"
}
```

* Sensor Reading

```
{
  "sensor_id": "string",
  "intersection_id": "string",
  "vehicle_count": int,
  "avg_speed": float,
  "timestamp": "datetime"
}
```

* Historical Traffic Data

```
{
  "intersection_id": "string",
  "timestamp": "datetime",
  "vehicle_count": int,
  "avg_speed": float,
  "congestion_level": "LOW/MEDIUM/HIGH"
}
```

***

## 25 – 40 min — High-level architecture & data flow

```
+-------------------+          +-------------------+           +-----------------+
| Traffic Sensors   | --data-> | Sensor Ingestion  | --data->  | Data Storage &  |
| (Cameras/Loops)  |          | & Processing      |           | Analytics       |
+-------------------+          +-------------------+           +-----------------+
        |                             |                                |
        |                             v                                |
        |                    +-------------------+                     |
        |                    | Traffic Signal    | <---commands-------+
        |                    | Controller        |
        |                    +-------------------+
        |                             |
        v                             v
Emergency Vehicle Detection --> Signal Override / Green Wave
```

Components

1. Traffic Sensors: loop detectors, cameras, radar; continuously send vehicle count, speed, congestion data.
2. Sensor Ingestion & Processing: stream ingestion (Kafka, MQTT), aggregate per intersection, normalize data.
3. Signal Controller: decides traffic light states based on current congestion, timing policies, emergency signals.
4. Analytics / Historical Storage: HDFS, time-series DB (InfluxDB, TimescaleDB), or cloud storage for historical analysis and predictions.
5. Emergency Vehicle Module: detects emergency vehicles (GPS or camera), calculates optimal green wave, updates relevant intersections.
6. Dashboard & Alerts: monitoring system for operators to see congestion, incidents, override controls.

***

## 40 – 50 min — Deep dive — adaptive algorithms, scaling, reliability

<br>

#### Adaptive Traffic Signal Algorithm

* Compute vehicle density per lane, avg wait time.
* Adjust green/red cycle durations using weighted round-robin or reinforcement learning.
* Update every few seconds to optimize flow.

<br>

#### Emergency Vehicle Prioritization

* Detect via GPS ping or camera recognition.
* Predict ETA per intersection, override signals for green wave.
* After emergency passes, restore normal operation smoothly.

<br>

#### Scaling & Distribution

* Partition city by zones or districts, each with local controller cluster.
* Edge processing at intersection: real-time decisions can happen locally for low-latency.
* Centralized system aggregates for analytics and coordination.

<br>

#### Fault tolerance

* Local intersection failure → default to safe blinking mode (all-red).
* Redundant signal controllers per intersection.
* Sensor redundancy to handle missing data.

***

## 50 – 55 min — Back-of-the-envelope calculations

<br>

Assumptions

* 1000 intersections, 6 sensors per intersection → 6k sensors.
* Each sensor sends 1 message/sec → 6k messages/sec total.
* Each message \~200 bytes → 1.2 MB/sec → \~100 GB/day (manageable).

<br>

Signal controller

* Edge controller latency: <1s for local decision.
* Central coordination updates: every 10–30s for zone-level optimization.

<br>

Storage

* Time-series DB: historical data per sensor \~ 200 bytes/sec → 1.2 MB/sec → 100 GB/day → 36 TB/year. Use compression.

***

## 55 – 58 min — Monitoring & ops

<br>

Monitoring

* Intersection status: signal states, failures, emergency overrides.
* Sensor health: online/offline, data quality.
* Traffic metrics: congestion per zone, queue lengths, average speed.

<br>

Alerts

* Sensor failures.
* Intersection hardware issues.
* Unusual congestion spikes or accidents.

<br>

Operational playbooks

* Manual override in case of emergency or hardware failure.
* Maintenance scheduling for sensors and lights.
* Incident response: rerouting traffic if accident occurs.

***

## 58 – 60 min — Trade-offs, evolution & summary

<br>

#### Trade-offs

* Centralized vs Edge control: Edge = low latency, fault-tolerant; Central = easier coordination.
* Polled vs stream-based sensor data: Stream provides real-time updates, but more complex.
* Complex ML prediction vs simple heuristics: ML more adaptive but needs training, infrastructure, and monitoring.
* Redundancy vs cost: full redundant controllers and sensors increase reliability but higher cost.

<br>

#### Evolution

1. MVP: simple fixed-timing + basic adaptive signal using local sensors.
2. Phase 2: dynamic adaptive algorithm per intersection, zone coordination, emergency vehicle prioritization.
3. Phase 3: ML-based congestion prediction, multi-modal traffic, predictive rerouting, city-wide optimization.

<br>

#### Summary

<br>

Design a distributed, adaptive, fault-tolerant traffic control system:

* Sensors feed real-time traffic data.
* Local controllers make low-latency signal decisions.
* Central analytics aggregates historical and live data.
* System prioritizes emergency vehicles, adapts signal timing, monitors performance, and scales across a city while maintaining safety and reliability.

***

If you want, I can next produce a sequence diagram for a single intersection adaptive control flow, which would make it visually easy to explain in an interview.

<br>

Do you want me to create that?
