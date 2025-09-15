#!/usr/bin/env ts-node

import { FileStreamMonitor } from '../services/FileStreamMonitor';
import path from 'path';

async function main() {
  const monitor = new FileStreamMonitor({
    claudeProjectsPath: path.join(process.env.HOME || '~', '.claude', 'projects'),
    pollInterval: 1000,
    usePolling: false
  });

  // Set up event listeners
  monitor.on('started', () => {
    console.log('✓ File monitoring started');
    console.log(`Watching: ${path.join(process.env.HOME || '~', '.claude', 'projects')}/**/*.jsonl`);
  });

  monitor.on('sessionDiscovered', ({ filePath, sessionInfo }) => {
    console.log('\n📁 New session discovered:');
    console.log(`  Session ID: ${sessionInfo.sessionId}`);
    console.log(`  Project: ${sessionInfo.projectPath}`);
    console.log(`  File: ${filePath}`);
  });

  monitor.on('existingMessage', (message) => {
    console.log('\n📜 Existing message:');
    console.log(`  [${message.timestamp}] ${message.type}: ${message.message.content.substring(0, 100)}...`);
  });

  monitor.on('newMessage', (message) => {
    console.log('\n✨ New message:');
    console.log(`  [${message.timestamp}] ${message.type}: ${message.message.content.substring(0, 100)}...`);
  });

  monitor.on('sessionTerminated', ({ sessionInfo }) => {
    console.log('\n❌ Session terminated:');
    console.log(`  Session ID: ${sessionInfo.sessionId}`);
    console.log(`  Project: ${sessionInfo.projectPath}`);
  });

  monitor.on('parseError', ({ error, line, filePath }) => {
    console.log('\n⚠️ Parse error:');
    console.log(`  File: ${filePath}`);
    console.log(`  Error: ${error}`);
    console.log(`  Line: ${line.substring(0, 100)}...`);
  });

  monitor.on('error', (error) => {
    console.error('\n❌ Monitor error:', error);
  });

  monitor.on('stopped', () => {
    console.log('\n🛑 File monitoring stopped');
  });

  try {
    await monitor.start();

    // Keep running until Ctrl+C
    process.on('SIGINT', async () => {
      console.log('\n\nShutting down...');
      await monitor.stop();
      process.exit(0);
    });

    // Display current status
    setTimeout(() => {
      const files = monitor.getMonitoredFiles();
      console.log(`\n📊 Currently monitoring ${files.length} files:`);
      files.forEach(file => {
        console.log(`  - ${file}`);
      });

      if (files.length === 0) {
        console.log('\n💡 No Claude Code sessions found.');
        console.log('Start a Claude Code session to see real-time monitoring in action!');
      }
    }, 2000);

  } catch (error) {
    console.error('Failed to start monitoring:', error);
    process.exit(1);
  }
}

if (require.main === module) {
  main().catch(console.error);
}