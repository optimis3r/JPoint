'use client';
import { useEffect, useRef } from 'react';
import * as d3 from 'd3';

export default function LeakGraph({ suspects }: { suspects: any[] }) {
  const svgRef = useRef<SVGSVGElement>(null);

  useEffect(() => {
    if (!svgRef.current || !suspects || suspects.length === 0) return;

    // 1. Setup SVG Canvas
    const width = 600;
    const height = 400;
    const svg = d3.select(svgRef.current)
      .attr('width', '100%')
      .attr('height', height)
      .attr('viewBox', `0 0 ${width} ${height}`)
      .style('background', '#0a0a0a')
      .style('border-radius', '0.5rem')
      .style('border', '1px solid #1f2937');

    svg.selectAll('*').remove(); // Clear previous renders

    // 2. Parse the Data
    const nodes = suspects.map((suspect, i) => {
      const match = suspect.description.match(/occupies ([\d,]+) \(([\d.]+)%\) bytes/);
      const percentage = match ? parseFloat(match[2]) : 20; 
      
      return {
        id: suspect.suspectClasses[0] || `Suspect-${i}`,
        radius: Math.max(percentage, 15), 
        percentage: match ? match[2] : 'Unknown',
        x: width / 2 + (Math.random() - 0.5) * 50,
        y: height / 2 + (Math.random() - 0.5) * 50,
      };
    });

    const graphNodes = [{ id: 'JVM Heap', radius: 10, percentage: '100', fx: width / 2, fy: height / 2 }, ...nodes];

    // 3. Force Simulation
    const simulation = d3.forceSimulation(graphNodes as any)
      .force('charge', d3.forceManyBody().strength(-200))
      .force('collide', d3.forceCollide().radius((d: any) => d.radius + 5))
      .force('center', d3.forceCenter(width / 2, height / 2));

    // 4. Draw Nodes
    const node = svg.append('g')
      .selectAll('circle')
      .data(graphNodes)
      .join('circle')
      .attr('r', (d) => d.radius)
      .attr('fill', (d, i) => i === 0 ? '#374151' : '#f43f5e') 
      .attr('stroke', (d, i) => i === 0 ? '#4b5563' : '#9f1239')
      .attr('stroke-width', 2);

    // 5. Add Labels
    const label = svg.append('g')
      .selectAll('text')
      .data(graphNodes)
      .join('text')
      .text((d) => d.id)
      .attr('font-size', '12px')
      .attr('fill', '#d1d5db')
      .attr('text-anchor', 'middle')
      .attr('dy', '-1em');

    const subLabel = svg.append('g')
      .selectAll('text')
      .data(graphNodes)
      .join('text')
      .text((d, i) => i === 0 ? '' : `${d.percentage}%`)
      .attr('font-size', '10px')
      .attr('fill', '#fbcfe8')
      .attr('text-anchor', 'middle')
      .attr('dy', '0.5em');

    // 6. Animate
    simulation.on('tick', () => {
      node
        .attr('cx', (d: any) => Math.max(d.radius, Math.min(width - d.radius, d.x)))
        .attr('cy', (d: any) => Math.max(d.radius, Math.min(height - d.radius, d.y)));
      
      label
        .attr('x', (d: any) => d.x)
        .attr('y', (d: any) => d.y);

      subLabel
        .attr('x', (d: any) => d.x)
        .attr('y', (d: any) => d.y);
    });

  }, [suspects]);

  return <svg ref={svgRef}></svg>;
}