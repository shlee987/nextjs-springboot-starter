'use client';

import { FilesPanel } from '@/features/files/FilesPanel';
import { ExampleSection } from './ExampleSection';

/** 공통 업로드와 본인 목록을 연결한다. 인증 경계는 유지하고 패널 내부 데이터만 세대별로 분리한다. */
export function FilesExample() {
  return (
    <ExampleSection
      title="이미지 업로드"
      description="이미지를 올리고, 저장한 파일을 확인하거나 내려받을 수 있어요."
      code={`const upload = async ({ file, signal, onProgress }: UploadRequest) => {
  const response = await uploadFile({ file }, {
    signal,
    onUploadProgress: (event) => onProgress(event.loaded, event.total),
  });
  readEnvelope(fileInfoSchema, response);
};
<FileUpload actionKey="files:upload" upload={upload} />`}
      purpose="이미지를 한곳에 모아 선택하고, 파일별 상태를 확인하세요."
      tryIt="로그인 후 이미지를 선택해 업로드하세요. 내 파일에서 다운로드하거나 삭제할 수 있습니다."
      expectedResult="서버 저장 완료와 파일별 오류를 구분합니다. 저장한 파일은 본인 목록에서 관리할 수 있어요."
    >
      <FilesPanel />
    </ExampleSection>
  );
}
