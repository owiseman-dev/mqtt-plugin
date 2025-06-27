'use client';

import React from 'react';

interface NodeRedEditorProps {
  className?: string;
}

const NodeRedEditor: React.FC<NodeRedEditorProps> = ({ className }) => {

  return (
    <div className={`node-red-editor ${className || ''}`} style={{ width: '100%', height: '100vh', position: 'relative' }}>
      <iframe
        src="http://127.0.0.1:1880/"
        style={{
          width: '100%',
          height: '100%',
          border: 'none',
          borderRadius: '4px'
        }}
        title="Node-RED Editor"
        allow="fullscreen"
        sandbox="allow-same-origin allow-scripts allow-forms allow-popups allow-modals"
      />
    </div>
  );
};

export default NodeRedEditor;