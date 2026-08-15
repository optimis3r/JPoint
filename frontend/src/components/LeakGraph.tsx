'use client';
import { useEffect, useRef } from 'react';
import * as d3 from 'd3';

function formatShortName(fullName: string): string {
  if (!fullName) return 'Unknown Suspect';
  let clean = fullName.split('(')[0].trim();
  const parts = clean.split('.');
  if (parts.length > 2) {
    clean = parts.slice(-2).join('.');
  }
  return clean.length > 28 ? clean.substring(0, 26) + '…' : clean;
}

export default function LeakGraph({ suspects }: { suspects: any[] }) {
  const svgRef = useRef<SVGSVGElement>(null);

  useEffect(() => {
    if (!svgRef.current || !suspects || suspects.length === 0) return;

    // SVG canvas
    const width = 600;
    const height = 340;
    const svg = d3.select(svgRef.current)
      .attr('width', '100%')
      .attr('height', height)
      .attr('viewBox', `0 0 ${width} ${height}`)
      .style('background', '#030712')
      .style('border-radius', '0.75rem')
      .style('border', '1px solid #1f2937');

    svg.selectAll('*').remove();

    // Node setup
    const totalSuspects = suspects.length;
    const suspectNodes = suspects.map((suspect, i) => {
      const match = suspect.description?.match(/occupies ([\d,]+) \(([\d.]+)%\) bytes/);
      const percentage = match ? parseFloat(match[2]) : 25;
      const rawName = suspect.suspectClasses?.[0] || `Suspect-${i + 1}`;
      const radius = Math.min(44, Math.max(22, Math.pow(percentage, 0.55) * 3.5 + 12));
      const angle = (i / Math.max(1, totalSuspects)) * 2 * Math.PI - Math.PI / 2;
      const dist = 135;

      return {
        id: rawName,
        shortName: formatShortName(rawName),
        radius: radius,
        percentage: match ? match[2] : 'Unknown',
        x: width / 2 + Math.cos(angle) * dist,
        y: height / 2 + Math.sin(angle) * dist,
      };
    });

    const rootNode = {
      id: 'JVM Heap Root',
      shortName: 'JVM Heap',
      radius: 24,
      percentage: '100',
      x: width / 2,
      y: height / 2,
      fx: width / 2,
      fy: height / 2,
    };

    const graphNodes: any[] = [rootNode, ...suspectNodes];
    const graphLinks = suspectNodes.map((_, idx) => ({
      source: 0,
      target: idx + 1,
    }));

    // Force simulation
    const simulation = d3.forceSimulation(graphNodes)
      .force('link', d3.forceLink(graphLinks).distance(135).strength(1.0))
      .force('charge', d3.forceManyBody().strength(-600))
      .force('collide', d3.forceCollide().radius((d: any) => d.radius + 25))
      .force('radial', d3.forceRadial((_: any, i: number) => (i === 0 ? 0 : 135), width / 2, height / 2).strength(0.9));

    // Links
    const link = svg.append('g')
      .selectAll('line')
      .data(graphLinks)
      .join('line')
      .attr('stroke', '#374151')
      .attr('stroke-width', 2)
      .attr('stroke-dasharray', '4,4');

    // Nodes
    const nodeGroup = svg.append('g')
      .selectAll('g')
      .data(graphNodes)
      .join('g')
      .style('cursor', 'pointer');

    nodeGroup.append('circle')
      .attr('r', (d: any) => d.radius)
      .attr('fill', (_, i) => (i === 0 ? '#10b981' : '#f43f5e'))
      .attr('stroke', (_, i) => (i === 0 ? '#059669' : '#9f1239'))
      .attr('stroke-width', 2.5)
      .attr('filter', 'drop-shadow(0px 4px 10px rgba(0, 0, 0, 0.5))');

    nodeGroup.append('title')
      .text((d: any) => `${d.id}\nRetained Heap: ${d.percentage}%`);

    nodeGroup.append('text')
      .text((d: any) => d.shortName)
      .attr('font-size', (d: any, i: number) => (i === 0 ? '12px' : '11px'))
      .attr('font-weight', '600')
      .attr('fill', '#f3f4f6')
      .attr('text-anchor', 'middle')
      .attr('dy', '-0.3em');

    nodeGroup.append('text')
      .text((d: any, i: number) => (i === 0 ? 'Root' : `${d.percentage}% Heap`))
      .attr('font-size', '10px')
      .attr('font-weight', '500')
      .attr('fill', (_, i) => (i === 0 ? '#a7f3d0' : '#fecdd3'))
      .attr('text-anchor', 'middle')
      .attr('dy', '1.1em');

    // Tick update
    simulation.on('tick', () => {
      link
        .attr('x1', (d: any) => d.source.x)
        .attr('y1', (d: any) => d.source.y)
        .attr('x2', (d: any) => d.target.x)
        .attr('y2', (d: any) => d.target.y);

      nodeGroup.attr('transform', (d: any) => {
        const clampedX = Math.max(d.radius + 10, Math.min(width - d.radius - 10, d.x));
        const clampedY = Math.max(d.radius + 10, Math.min(height - d.radius - 10, d.y));
        return `translate(${clampedX},${clampedY})`;
      });
    });

  }, [suspects]);

  return <svg ref={svgRef}></svg>;
}