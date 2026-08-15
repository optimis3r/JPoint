import { NextResponse } from 'next/server';
import { S3Client, ListObjectsV2Command, GetObjectCommand } from '@aws-sdk/client-s3';

// MinIO client
const s3Client = new S3Client({
  region: 'us-east-1',
  endpoint: 'http://localhost:9000',
  forcePathStyle: true,
  credentials: {
    accessKeyId: 'admin',
    secretAccessKey: 'password123',
  },
});

export async function GET() {
  const bucketName = 'jpoint-raw-dumps';

  try {
    const listCommand = new ListObjectsV2Command({ Bucket: bucketName });
    const listedObjects = await s3Client.send(listCommand);

    if (!listedObjects.Contents || listedObjects.Contents.length === 0) {
      return NextResponse.json([]);
    }

    const reportObjects = listedObjects.Contents.filter((obj) => 
      obj.Key && obj.Key.endsWith('_report.json')
    );

    // Fetch reports
    const reports = await Promise.all(
      reportObjects.map(async (obj) => {
        const getCommand = new GetObjectCommand({
          Bucket: bucketName,
          Key: obj.Key,
        });
        const response = await s3Client.send(getCommand);
        const bodyString = await response.Body?.transformToString();
        const data = bodyString ? JSON.parse(bodyString) : null;
        if (data) {
          data.lastModified = obj.LastModified ? new Date(obj.LastModified).toISOString() : null;
        }
        return data;
      })
    );

    // Sort newest first
    const validReports = reports.filter(Boolean);
    validReports.sort((a, b) => {
      const timeA = a.lastModified ? new Date(a.lastModified).getTime() : 0;
      const timeB = b.lastModified ? new Date(b.lastModified).getTime() : 0;
      return timeB - timeA;
    });

    return NextResponse.json(validReports);
  } catch (error) {
    console.error('Error fetching reports:', error);
    return NextResponse.json({ error: 'Failed to fetch telemetry reports' }, { status: 500 });
  }
}